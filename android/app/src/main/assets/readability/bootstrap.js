/**
 * NovelLib Readability Bootstrap
 * Executes Mozilla Readability on a cloned DOM and provides safe chunked transfer.
 */
window.__novellib_readability_execute = function() {
    try {
        if (typeof Readability === 'undefined') {
            return JSON.stringify({ error: 'READABILITY_NOT_LOADED' });
        }
        var docClone = document.cloneNode(true);
        var reader = new Readability(docClone, {
            charThreshold: 150,
            classesToPreserve: ['chapter-content', 'entry-content', 'reading-content', 'text-content']
        });
        var article = reader.parse();
        if (!article || !article.content || (article.textContent || '').trim().length < 100) {
            return JSON.stringify({ error: 'NOT_READERABLE' });
        }

        var result = {
            title: article.title || '',
            byline: article.byline || '',
            excerpt: article.excerpt || '',
            content: article.content || '',
            textLength: (article.textContent || '').length,
            siteName: article.siteName || ''
        };

        var jsonStr = JSON.stringify(result);
        window.__novellib_reader_data = jsonStr;

        return JSON.stringify({
            success: true,
            totalLength: jsonStr.length,
            title: result.title,
            textLength: result.textLength
        });
    } catch (e) {
        return JSON.stringify({ error: 'EXCEPTION', message: (e && e.message) ? e.message : 'Unknown error' });
    }
};

window.__novellib_readability_get_chunk = function(offset, limit) {
    if (!window.__novellib_reader_data) return '';
    return window.__novellib_reader_data.substring(offset, offset + limit);
};

window.__novellib_readability_cleanup = function() {
    try {
        delete window.__novellib_reader_data;
    } catch (_) {}
};
