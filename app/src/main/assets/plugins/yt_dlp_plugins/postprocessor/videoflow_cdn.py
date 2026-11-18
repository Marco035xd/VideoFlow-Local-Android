"""Per-process recovery using only media mirrors advertised by YouTube.

Ported from VideoFlow Local Windows 1.6.19. Loaded through yt-dlp's plugin API;
the upstream engine archive is never edited. TLS and signed queries are retained.
"""
import re
from urllib.parse import parse_qs, unquote, urlsplit, urlunsplit
from yt_dlp.networking import Request
from yt_dlp.networking.exceptions import HTTPError, ProxyError, RequestError, SSLError
from yt_dlp.postprocessor.common import PostProcessor


def candidates(url):
    try:
        parts = urlsplit(url)
        if parts.scheme != 'https' or parts.username or parts.password or parts.port not in (None, 443):
            return [url]
        host = re.fullmatch(r'(r{1,2}\d{1,3})(---|\.)(sn-[a-z0-9-]{1,80})\.googlevideo\.com', parts.hostname or '')
        if not host or not re.match(r'^/(?:videoplayback(?:/|$)|api/manifest/)', parts.path):
            return [url]
        fields = parse_qs(parts.query).get('mn', [])
        if len(fields) == 1:
            advertised = fields[0].split(',')
        elif not fields:
            match = re.search(r'(?:^|/)mn/([^/]+)', parts.path)
            advertised = unquote(match[1]).split(',') if match else []
        else:
            return [url]
        if host[3] not in advertised:
            return [url]
        result = [url]
        for server in advertised[:8]:
            if not re.fullmatch(r'sn-[a-z0-9-]{1,80}', server) or server == host[3]:
                continue
            netloc = host[1] + host[2] + server + '.googlevideo.com' + (':443' if parts.port == 443 else '')
            candidate = urlunsplit(parts._replace(netloc=netloc))
            if candidate not in result:
                result.append(candidate)
            if len(result) == 3:
                break
        return result
    except (TypeError, ValueError):
        return [url]


def transient(error):
    if isinstance(error, (SSLError, ProxyError)):
        return False
    if isinstance(error, HTTPError):
        return error.status in {500, 502, 503, 504}
    message = str(error).lower()
    if 'certificate' in message:
        return False
    return any(word in message for word in ('timed out', 'timeout', 'connection reset', 'connection refused', 'connection aborted', 'unreachable', 'name resolution', 'getaddrinfo failed'))


class VideoFlowCDNPP(PostProcessor):
    def __init__(self, downloader=None):
        super().__init__(downloader)
        self._original = downloader.urlopen
        self._preferred = {}
        downloader.urlopen = self._urlopen

    def run(self, info):
        return [], info

    def _urlopen(self, request):
        if not isinstance(request, (str, Request)):
            return self._original(request)
        original = Request(request) if isinstance(request, str) else request
        if original.method not in {'GET', 'HEAD'} or original.data is not None:
            return self._original(request)
        urls = candidates(original.url)
        if len(urls) < 2:
            return self._original(request)
        origin_host = urlsplit(original.url).hostname
        preferred = self._preferred.get(origin_host)
        urls.sort(key=lambda url: urlsplit(url).hostname != preferred)
        for index, url in enumerate(urls):
            current = original.copy()
            current.update(url=url)
            if 'Host' in current.headers:
                current.headers['Host'] = urlsplit(url).netloc
            current.extensions['timeout'] = min(float(current.extensions.get('timeout') or self._downloader.params.get('socket_timeout') or 20), 12)
            if url != original.url and not self._downloader.params.get('quiet'):
                print('[videoflow] alternate media server', flush=True)
            try:
                response = self._original(current)
            except RequestError as error:
                if not transient(error) or index == len(urls) - 1:
                    raise
                if isinstance(error, HTTPError):
                    error.close()
                continue
            self._preferred[origin_host] = urlsplit(url).hostname
            return response
