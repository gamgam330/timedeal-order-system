#!/usr/bin/env python3
"""posts/*.md → 티스토리 편집기에 넣을 HTML (이미지는 [[IMGn]] 자리표시로 바꾼다).
사용법: python3 scripts/md2html.py posts/01-xxx.md out.json   (markdown 패키지 필요)
python-markdown은 목록 앞 빈 줄과 4칸 하위 목록을 요구하므로 GitHub식 마크다운을 보정한다."""
import json, re, sys
import markdown

src = open(sys.argv[1]).read().splitlines()
title = src[0].lstrip('# ').strip()
list_re = re.compile(r'^(> )?\s*([-*]|\d+\.) ')
out, in_fence = [], False
for line in src[1:]:
    if line.lstrip().startswith('```'):
        in_fence = not in_fence
    if not in_fence:
        line = re.sub(r'^(> )?  (- )', r'\1    \2', line)
        prev = out[-1] if out else ''
        if list_re.match(line) and prev.strip() not in ('', '>') and not list_re.match(prev):
            out.append('>' if line.startswith('>') else '')
    out.append(line)

imgs = []
def placeholder(m):
    imgs.append(m.group(2))
    return f'\n\n<p>[[IMG{len(imgs)}]]</p>\n\n'
body = re.sub(r'!\[([^\]]*)\]\(([^)]+)\)', placeholder, '\n'.join(out))
html = markdown.markdown(body, extensions=['tables', 'fenced_code'])

# 티스토리 + hELLO 스킨용 서식
# - 인용구: "정리"로 시작하면 왼쪽 세로선(style2), 나머지는 회색 박스(style3). 지정이 없으면 hELLO가 가운데 큰 따옴표로 그린다
# - 표: hELLO는 data-ke-align을 정렬에 쓰지 않으므로 폭을 직접 지정하고 셀을 가운데 정렬한다
# - 코드 블록: 언어를 지정하지 않으면 highlight.js가 엉뚱한 언어로 추측한다
def blockquote(m):
    style = 'style2' if m.group(1).lstrip().startswith('<p><strong>정리') else 'style3'
    return f'<blockquote data-ke-style="{style}">{m.group(1)}</blockquote>'
html = re.sub(r'<blockquote>(.*?)</blockquote>', blockquote, html, flags=re.S)
html = re.sub(r'<table>', '<table style="width: 100%; margin-left: auto; margin-right: auto;" data-ke-align="alignCenter">', html)
html = re.sub(r'<(td|th)(?: style="[^"]*")?>', r'<\1 style="text-align: center;">', html)
def codeblock(m):
    lang = {'text': 'plaintext', '': 'plaintext'}.get(m.group(1) or '', m.group(1))
    return f'<pre class="{lang}" data-ke-language="{lang}" data-ke-type="codeblock"><code class="language-{lang}">'
html = re.sub(r'<pre><code(?: class="language-(\w+)")?>', codeblock, html)
json.dump({'title': title, 'html': html, 'imgs': imgs}, open(sys.argv[2], 'w'), ensure_ascii=False)
print(title, len(html), imgs)
