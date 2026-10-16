import MarkdownIt from 'markdown-it'
import createDOMPurify from 'dompurify'
import { renderToString } from 'katex'
import { fail } from './protocol'

const markdown = new MarkdownIt({ html: false, linkify: false, typographer: false })
// 来源中的图片与链接必须通过获授权内容接口打开。
markdown.disable(['image', 'link', 'autolink'])
const purifier = createDOMPurify(window)
if (!purifier.isSupported) fail('DEPENDENCY_UNAVAILABLE', '当前浏览器无法执行内容安全校验')
purifier.addHook('uponSanitizeAttribute', (_element, attribute) => {
  if (['href', 'xlink:href', 'action', 'formaction', 'srcset'].includes(attribute.attrName)) attribute.keepAttr = false
  if (attribute.attrName === 'src' && !/^data:image\/(?:png|jpeg|gif|webp|bmp);base64,/.test(attribute.attrValue)) attribute.keepAttr = false
})

export function renderSourceMarkdown(text: string): string {
  return purifier.sanitize(markdown.render(text), { USE_PROFILES: { html: true },
    FORBID_TAGS: ['style', 'img', 'iframe', 'object', 'embed', 'form'], FORBID_ATTR: ['style'], SANITIZE_NAMED_PROPS: true })
}

export function renderDocumentFormula(latex: string): string {
  return renderToString(latex, { displayMode: true, throwOnError: true, trust: false, strict: 'error',
    output: 'htmlAndMathml', maxExpand: 1000, macros: {} })
}

export function sourcePreviewDocument(element: HTMLElement): string {
  const content = purifier.sanitize(element.innerHTML, { USE_PROFILES: { html: true, svg: true, mathMl: true },
    ADD_TAGS: ['style'], FORCE_BODY: true, SANITIZE_NAMED_PROPS: true,
    FORBID_TAGS: ['script', 'iframe', 'object', 'embed', 'form', 'input', 'button', 'video', 'audio', 'link'] })
  return '<!doctype html><html lang="zh-CN"><head><meta charset="UTF-8"><meta http-equiv="Content-Security-Policy" '
    + 'content="default-src &apos;none&apos;; img-src data:; font-src data:; style-src &apos;unsafe-inline&apos;; base-uri &apos;none&apos;; form-action &apos;none&apos;">'
    + '<style>body{margin:0;color:#243247;background:#f8fafc;font:14px/1.6 sans-serif}table{border-collapse:collapse}img{max-width:100%}</style>'
    + '</head><body>' + content + '</body></html>'
}
