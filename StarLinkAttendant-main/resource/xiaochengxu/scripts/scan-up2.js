// 诊断脚本 2：把 wxml 里的 u-p 绑定和同目录 js 里的渲染赋值对照起来
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

for (const wxml of walk(ROOT)) {
  const js = wxml.replace(/\.wxml$/, '.js')
  if (!fs.existsSync(js)) continue
  const jsSrc = fs.readFileSync(js, 'utf8')
  const wxmlSrc = fs.readFileSync(wxml, 'utf8')

  // js 中所有被赋值为 common_vendor.p(...) 的 key
  const pKeys = new Set()
  const pRe = /(?:^|[\s,{])([A-Za-z_][\w]*):\s*common_vendor\.p\(/gm
  let m
  while ((m = pRe.exec(jsSrc))) pKeys.add(m[1])

  // wxml 中每个 u-p 表达式，剥掉 ||'' 之类
  const re = /<([a-zA-Z][\w-]*)\b([^>]*?)u-p="\{\{([^}]*)\}\}"/g
  const bad = []
  while ((m = re.exec(wxmlSrc))) {
    const tag = m[1]
    const expr = m[3].trim()
    const base = expr.split('||')[0].trim()
    const last = base.split('.').pop()
    // 只检查非 for-item 的简单 key（for 场景 key 是 item 内的子键，跳过）
    if (base.includes('.')) continue
    if (!pKeys.has(last)) bad.push({ tag, expr, last })
  }

  if (bad.length) {
    console.log('\n### ' + path.relative(ROOT, wxml) + ' : ' + bad.length + ' 个 u-p 不是 common_vendor.p(...)')
    for (const b of bad) console.log('   <' + b.tag + '>  u-p=' + b.expr + '   (key "' + b.last + '")')
    // 打印 js 里这个 key 的赋值
    for (const b of new Set(bad.map((x) => x.last))) {
      const kRe = new RegExp('(^|[\\s,{])' + b + ':\\s*([^,\\n]*)', 'm')
      const km = kRe.exec(jsSrc)
      if (km) console.log('      js ' + b + ' = ' + km[2].slice(0, 120))
    }
  }
}
