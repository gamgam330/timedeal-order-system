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
json.dump({'title': title, 'html': html, 'imgs': imgs}, open(sys.argv[2], 'w'), ensure_ascii=False)
print(title, len(html), imgs)
