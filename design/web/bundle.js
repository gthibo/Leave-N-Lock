/* @ds-bundle: {"format":4,"namespace":"Porcelain","components":[{"name":"Button"},{"name":"Label"},{"name":"Field"},{"name":"Switch"},{"name":"Radio"},{"name":"Slider"},{"name":"SettingRow"},{"name":"Readout"},{"name":"SegmentMeter"},{"name":"StatTile"},{"name":"Card"},{"name":"TopBar"},{"name":"PixelGrid"},{"name":"Mark"},{"name":"Icon"}]} */
(function () {
  var R = window.React, h = R.createElement;
  function cx() { return Array.prototype.filter.call(arguments, Boolean).join(' '); }
  function omit(o, keys) { var r = {}; for (var k in o) if (keys.indexOf(k) < 0) r[k] = o[k]; return r; }
  var uid = 0; function useId(given) { var r = R.useRef(given || ('pg-' + (++uid))); return r.current; }

  var ICONS = {
    battery: [['rect', { x: 3, y: 7, width: 16, height: 10 }], ['path', { d: 'M19 10.5h2v3h-2M6 10v4M9 10v4M12 10v4' }]],
    bolt: [['path', { d: 'M13 2 5 14h6l-1 8 8-12h-6l1-8z' }]],
    plug: [['path', { d: 'M9 3v5M15 3v5M6 8h12v4a6 6 0 0 1-12 0V8zM12 18v3' }]],
    thermo: [['path', { d: 'M10 4h4v10.2a3.5 3.5 0 1 1-4 0V4zM12 10v6' }]],
    route: [['rect', { x: 4, y: 16, width: 4, height: 4 }], ['rect', { x: 16, y: 4, width: 4, height: 4 }], ['path', { d: 'M8 18h6a3 3 0 0 0 0-6h-4a3 3 0 0 1 0-6h6' }]],
    lock: [['rect', { x: 5, y: 11, width: 14, height: 10 }], ['path', { d: 'M8 11V8a4 4 0 0 1 8 0v3M12 15v2' }]],
    unlock: [['rect', { x: 5, y: 11, width: 14, height: 10 }], ['path', { d: 'M8 11V8a4 4 0 0 1 7.5-2M12 15v2' }]],
    car: [['path', { d: 'M3 17v-5l2.5-5h13l2.5 5v5H3zM3 12h18M6 17v2M18 17v2M6.5 14.5h2M15.5 14.5h2' }]],
    bluetooth: [['path', { d: 'M7 7l10 10-5 4V3l5 4L7 17' }]],
    pin: [['path', { d: 'M12 21s-6-6-6-11a6 6 0 0 1 12 0c0 5-6 11-6 11z' }], ['rect', { x: 10, y: 8, width: 4, height: 4 }]],
    refresh: [['path', { d: 'M20 12a8 8 0 1 1-2.3-5.7M20 4v5h-5' }]],
    sliders: [['path', { d: 'M4 7h9M17 7h3M4 17h3M11 17h9' }], ['rect', { x: 13, y: 5, width: 4, height: 4 }], ['rect', { x: 7, y: 15, width: 4, height: 4 }]],
    help: [['rect', { x: 3, y: 3, width: 18, height: 18 }], ['path', { d: 'M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .8-1 1.5V14M12 17v.5' }]],
    'arrow-left': [['path', { d: 'M20 12H5M11 6l-6 6 6 6' }]],
    'arrow-right': [['path', { d: 'M4 12h15M13 6l6 6-6 6' }]],
    sun: [['rect', { x: 8, y: 8, width: 8, height: 8 }], ['path', { d: 'M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9 7 7M17 17l2.1 2.1M4.9 19.1 7 17M17 7l2.1-2.1' }]],
    grid: [['rect', { x: 4, y: 4, width: 6, height: 6 }], ['rect', { x: 14, y: 4, width: 6, height: 6 }], ['rect', { x: 4, y: 14, width: 6, height: 6 }], ['rect', { x: 14, y: 14, width: 6, height: 6 }]],
    clock: [['rect', { x: 3, y: 3, width: 18, height: 18 }], ['path', { d: 'M12 7v5l3 3' }]]
  };
  function Icon(p) {
    var parts = ICONS[p.name] || ICONS.grid, s = p.size || 20;
    return h('svg', { className: cx('pg-icon', p.className), width: s, height: s, viewBox: '0 0 24 24', 'aria-hidden': p.title ? undefined : 'true', role: p.title ? 'img' : undefined },
      p.title ? h('title', null, p.title) : null,
      parts.map(function (q, i) { return h(q[0], Object.assign({ key: i }, q[1])); }));
  }
  Icon.names = Object.keys(ICONS);

  function Button(p) {
    var v = p.variant || 'secondary';
    var rest = omit(p, ['variant', 'size', 'icon', 'iconAfter', 'block', 'className', 'children']);
    return h('button', Object.assign({ type: 'button' }, rest, { className: cx('pg-btn', 'pg-btn-' + v, p.size === 'sm' && 'pg-btn-sm', p.block && 'pg-btn-block', p.className) }),
      p.icon ? h(Icon, { name: p.icon, size: 18 }) : null, h('span', null, p.children), p.iconAfter ? h(Icon, { name: p.iconAfter, size: 18 }) : null);
  }

  function Label(p) {
    var tone = p.tone || 'neutral';
    return h('span', { className: cx('pg-label', tone !== 'neutral' && 'pg-label-' + tone, p.className) }, p.dot === false ? null : h('i', { 'aria-hidden': 'true' }), p.children);
  }

  function Field(p) {
    var id = useId(p.id), rest = omit(p, ['label', 'unit', 'hint', 'id', 'className']);
    return h('div', { className: cx('pg-field', p.className) },
      h('label', { htmlFor: id }, p.label),
      h('div', { className: 'pg-input-wrap' }, h('input', Object.assign({ className: 'pg-input', id: id }, rest)), p.unit ? h('span', { className: 'pg-input-unit' }, p.unit) : null),
      p.hint ? h('small', null, p.hint) : null);
  }

  function Switch(p) {
    var st = R.useState(!!p.defaultChecked), on = p.checked != null ? p.checked : st[0];
    function toggle() { var n = !on; if (p.checked == null) st[1](n); if (p.onChange) p.onChange(n); }
    return h('button', { type: 'button', role: 'switch', 'aria-checked': on ? 'true' : 'false', 'aria-label': p.label, className: cx('pg-switch', p.className), onClick: toggle, disabled: p.disabled, id: p.id });
  }

  function SettingRow(p) {
    return h('div', { className: cx('pg-setting', p.className) },
      h('div', { className: 'pg-setting-text' }, h('div', { className: 'pg-setting-title' }, p.title), p.description ? h('div', { className: 'pg-setting-desc' }, p.description) : null),
      p.control || null);
  }

  function Radio(p) {
    var name = useId(p.name), st = R.useState(p.defaultValue), val = p.value != null ? p.value : st[0];
    return h('div', { className: 'pg-radios', role: 'radiogroup', 'aria-label': p.label },
      (p.options || []).map(function (o) {
        return h('label', { key: o.value, className: 'pg-radio' },
          h('input', { type: 'radio', name: name, value: o.value, checked: val === o.value, onChange: function () { if (p.value == null) st[1](o.value); if (p.onChange) p.onChange(o.value); } }),
          h('i', { 'aria-hidden': 'true' }), h('span', null, o.label, o.hint ? h('small', null, o.hint) : null));
      }));
  }

  function Slider(p) {
    var id = useId(p.id), min = p.min == null ? 0 : p.min, max = p.max == null ? 100 : p.max;
    var st = R.useState(p.defaultValue != null ? p.defaultValue : min), v = p.value != null ? p.value : st[0];
    var pct = ((v - min) / (max - min) * 100) + '%';
    return h('div', { className: 'pg-slider' },
      h('div', { className: 'pg-slider-head' }, h('label', { htmlFor: id, className: 'pg-setting-title' }, p.label), h('span', null, v + (p.unit ? ' ' + p.unit : ''))),
      h('input', { id: id, type: 'range', min: min, max: max, step: p.step || 1, value: v, style: { '--pct': pct }, onChange: function (e) { var n = Number(e.target.value); if (p.value == null) st[1](n); if (p.onChange) p.onChange(n); } }),
      p.scale === false ? null : h('div', { className: 'pg-slider-scale', 'aria-hidden': 'true' }, h('span', null, min + (p.unit ? ' ' + p.unit : '')), h('span', null, max + (p.unit ? ' ' + p.unit : ''))));
  }

  function Readout(p) {
    return h('div', { className: cx('pg-readout', p.size === 'sm' && 'pg-readout-sm', p.className) },
      p.label ? h('div', { className: 'pg-eyebrow' }, p.label) : null,
      h('div', { className: 'pg-readout-value' }, h('span', null, p.value), p.unit ? h('span', { className: 'pg-readout-unit' }, p.unit) : null),
      p.note ? h('div', { className: 'pg-readout-note' }, p.note) : null);
  }

  function SegmentMeter(p) {
    var n = p.segments || 20, v = Math.max(0, Math.min(100, p.value || 0)), lit = Math.round(v / 100 * n), cells = [];
    for (var i = 0; i < n; i++) cells.push(h('span', { key: i, className: i < lit - 1 ? 'on' : (i === lit - 1 ? 'lead' : '') }));
    return h('div', { className: p.className, role: 'meter', 'aria-valuemin': 0, 'aria-valuemax': 100, 'aria-valuenow': v, 'aria-label': p.label || 'Level' },
      h('div', { className: cx('pg-meter', p.tone === 'signal' && 'pg-meter-signal'), style: { gridTemplateColumns: 'repeat(' + n + ', 1fr)' } }, cells),
      p.scale ? h('div', { className: 'pg-meter-scale', 'aria-hidden': 'true' }, h('span', null, '0'), h('span', null, '50'), h('span', null, '100')) : null);
  }

  function StatTile(p) {
    return h('div', { className: cx('pg-tile', p.className) }, p.icon ? h(Icon, { name: p.icon, size: 20 }) : null, h('div', null, h('b', null, p.value), h('span', null, p.label)));
  }

  // pattern rows: '.' empty, '1' ink, 't' teal, 'm' mint, 's' silver, 'l' line, 'a' signal (amber), 'w' on-teal-deep, 'd' teal-deep-raised
  var TONES = { '1': 'pg-px-ink', t: 'pg-px-teal', m: 'pg-px-mint', s: 'pg-px-silver', l: 'pg-px-line', a: 'pg-px-signal', w: 'pg-px-light', d: 'pg-px-dim' };
  function PixelGrid(p) {
    var rows = p.pattern || ['t.t.', '.m.t', 't.t.'], c = p.cell || 8, g = p.gap == null ? 2 : p.gap;
    var w = rows[0].length * (c + g) - g, ht = rows.length * (c + g) - g, rects = [];
    rows.forEach(function (row, y) { for (var x = 0; x < row.length; x++) { var k = TONES[row[x]]; if (k) rects.push(h('rect', { key: x + '-' + y, className: k, x: x * (c + g), y: y * (c + g), width: c, height: c })); } });
    return h('svg', { className: cx('pg-pixels', p.className), width: w, height: ht, viewBox: '0 0 ' + w + ' ' + ht, 'aria-hidden': 'true', style: p.style }, rects);
  }

  function Card(p) {
    var tone = p.tone || 'raised';
    return h('section', { className: cx('pg-card', tone !== 'raised' && 'pg-card-' + tone, p.cut && 'pg-card-cut-' + p.cut, p.className), style: p.style, 'aria-label': typeof p.title === 'string' ? p.title : p.eyebrow },
      h('div', { className: 'pg-card-in', style: p.bodyStyle },
        p.pixels ? h(PixelGrid, { pattern: p.pixels, className: cx('pg-card-pixels', p.pixelsAt === 'bottom' && 'pg-card-pixels-bottom'), cell: 6 }) : null,
        p.eyebrow || p.title || p.meta ? h('header', { className: 'pg-card-head' },
          h('div', null, p.eyebrow ? h('div', { className: 'pg-eyebrow' }, p.eyebrow) : null, p.title ? h('h3', { className: 'pg-card-title' }, p.title) : null),
          p.meta || null) : null,
        p.children));
  }

  function TopBar(p) {
    return h('header', { className: cx('pg-topbar', p.className) },
      p.onBack !== undefined || p.back ? h('button', { type: 'button', className: 'pg-iconbtn pg-focus', 'aria-label': 'Back', onClick: p.onBack }, h(Icon, { name: 'arrow-left', size: 24 })) : null,
      p.brand ? h('div', { className: 'pg-topbar-brand' }, p.brand) : h('h1', null, p.title),
      (p.actions || []).map(function (a) { return h('button', { key: a.icon, type: 'button', className: 'pg-iconbtn pg-focus', 'aria-label': a.label, onClick: a.onClick }, h(Icon, { name: a.icon, size: 24 })); }));
  }

  // The LEAVE/LOCK mark, drawn from the app icon: a squared open padlock split on the diagonal,
  // the Bluetooth rune on the body, a 4x4 pixel dissolve on the shackle with one amber cell, and three signal arcs.
  // `compact` drops the arcs for 24px and under. Geometry is in the app icon's 512 coordinates.
  var RUNE = 'M789 1064V0l1281 1012-702 643 702 661L778 3334l12-1123-544 532L0 2509l789-737v-263L5 778l246-251 538 538zm344-352l363 305-363 292V712zm0 1304l363 305-363 292v-597z';
  var DISSOLVE = ['llll', 'lmlm', 'm.ma', 'd.d.'];
  var DCLS = { l: 'mk-lo', m: 'mk-mid', d: 'mk-dim', a: 'mk-sig' };
  function Mark(p) {
    var size = p.size || 32, full = !p.compact;
    var vb = full ? [128, 74, 314, 374] : [128, 74, 252, 374];
    var w = size * vb[2] / vb[3], cells = [];
    DISSOLVE.forEach(function (row, j) { for (var i = 0; i < 4; i++) { var c = row[i]; if (c !== '.') cells.push(h('rect', { key: i + '-' + j, className: DCLS[c], x: 312.5 + i * 8.25, y: 171.5 + j * 8.25, width: 8.25, height: 8.25 })); } });
    var g = h('svg', { className: cx('pg-mark-svg', p.tone === 'light' && 'pg-mark-light'), width: Math.round(w), height: size, viewBox: vb.join(' '), 'aria-hidden': p.word ? 'true' : undefined, role: p.word ? undefined : 'img', 'aria-label': p.word ? undefined : 'LEAVE/LOCK' },
      h('path', { className: 'mk-hi', d: 'M133.415 212.887V442.891L373.68 212.887Z' }),
      h('path', { className: 'mk-lo', d: 'M375.34 443.943V213.939L135.075 443.943Z' }),
      h('path', { className: 'mk-lo', d: 'M254.5 78h50l41 41v52.5h-33V135l-24-24h-34Z' }),
      h('path', { className: 'mk-hi', d: 'M254.5 78h-50l-41 41v85.5h33V135l24-24h34Z' }),
      cells,
      full ? h('g', { className: 'mk-arcs' },
        h('path', { d: 'M369 134c16.952 16.491 17.836 43.434 2 61', strokeWidth: 5 }),
        h('path', { d: 'M386 113c28.713 26.915 30.494 71.898 4 101', strokeWidth: 6.5 }),
        h('path', { d: 'M401 92c20.355 17.215 32.818 41.98 34.516 68.584s-7.515 52.753-25.516 72.416', strokeWidth: 8 })) : null,
      h('path', { className: 'mk-rune', d: RUNE, transform: 'translate(188.75 234.47) scale(0.05737)', fillRule: 'evenodd' }));
    if (!p.word) return h('span', { className: cx('pg-mark', p.className) }, g);
    var word = p.stacked ? h('span', { className: 'pg-mark-word pg-mark-stacked' }, 'LEAVE', h('br'), '/LOCK') : h('span', { className: 'pg-mark-word' }, 'LEAVE/LOCK');
    return h('span', { className: cx('pg-mark', p.tone === 'light' && 'pg-mark-on-deep', p.className), role: 'img', 'aria-label': 'LEAVE/LOCK', style: p.wordSize ? { fontSize: p.wordSize } : null }, g, word);
  }

  window.Porcelain = Object.assign(window.Porcelain || {}, { Button: Button, Label: Label, Field: Field, Switch: Switch, Radio: Radio, Slider: Slider, SettingRow: SettingRow, Readout: Readout, SegmentMeter: SegmentMeter, StatTile: StatTile, Card: Card, Panel: Card, TopBar: TopBar, PixelGrid: PixelGrid, Mark: Mark, Icon: Icon });
})();
