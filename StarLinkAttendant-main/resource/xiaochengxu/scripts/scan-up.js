// 临时诊断脚本：扫描编译产物中 u-p 的绑定表达式，定位异常绑定
const fs = require('fs')
const path = require('path')

const ROOT = path.join(__dirname, '..', 'dist', 'dev', 'mp-weixin')

function walk(dir, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name)
    if (e.isDirectory()) walk(p, out)
    else if (e.name.endsWith('.wxml')) out.push(p)
  }
  return out
}

const IDENT = /^[A-Za-z_][\w]*(\.[A-Za-z_][\w]*)*$/

const files = walk(ROOT)
let total = 0
const odd = []
const perComp = {}

for (const f of files) {
  const s = fs.readFileSync(f, 'utf8')
  const re = /<([a-zA-Z][\w-]*)\b[^>]*?u-p="\{\{([^}]*)\}\}"/g
  let m
  while ((m = re.exec(s))) {
    total++
    const comp = m[1]
    const expr = m[2].trim()
    perComp[comp] = (perComp[comp] || 0) + 1
    if (!IDENT.test(expr)) odd.push({ file: path.relative(ROOT, f), comp, expr })
  }
}

console.log('wxml files:', files.length, 'u-p bindings:', total)
console.log('\n--- u-p count per component tag ---')
Object.entries(perComp)
  .sort((a, b) => b[1] - a[1])
  .forEach(([k, v]) => console.log(String(v).padStart(4), k))

console.log('\n--- non-identifier u-p expressions ---')
odd.forEach((o) => console.log(o.file, '<' + o.comp + '>', '=>', o.expr))

// 找出 u-p 与 u-i 绑定到同一个变量（u-i 应是 uI）
console.log('\n--- u-i bound to a non-static / suspicious value ---')
for (const f of files) {
  const s = fs.readFileSync(f, 'utf8')
  const re = /<([a-zA-Z][\w-]*)\b[^>]*?u-p="\{\{([^}]*)\}\}"[^>]*?>/g
  let m
  while ((m = re.exec(s))) {
    const tag = m[0]
    const pExpr = m[2].trim()
    const ui = /u-i="\{\{([^}]*)\}\}"/.exec(tag)
    const up = /u-p="\{\{([^}]*)\}\}"/.exec(tag)
    if (ui && up && ui[1].trim() === up[1].trim()) {
      console.log('SAME EXPR for u-i and u-p:', path.relative(ROOT, f), '<' + m[1] + '>', pExpr)
    }
  }
}
