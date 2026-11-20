(() => {
  if (window.__vc) return;
  const nativeAttach = Element.prototype.attachShadow;
  Element.prototype.attachShadow = function(options) {
    return nativeAttach.call(this, {...options, mode: 'open'});
  };
  function notifyBridge() {
    try {
      const bridge = window.AndroidVoiceBridge || (window.parent && window.parent.AndroidVoiceBridge);
      if (bridge && typeof bridge.setHostname === 'function') {
        bridge.setHostname(location.hostname || '');
      }
    } catch (e) {}
  }
  notifyBridge();
  let revision = 0;
  const observed = new WeakSet();
  function observe(root) {
    if (observed.has(root)) return;
    observed.add(root);
    new MutationObserver(() => revision++).observe(root, {subtree:true, childList:true, attributes:true, characterData:true});
  }
  const ids = new WeakMap(); let nextId = 0; let targets = new Map();
  function publicText(el) {
    let text = el.innerText || '';
    for (const field of el.querySelectorAll?.('textarea,select,[contenteditable]') || []) {
      for (const value of [field.innerText, field.textContent]) if (value) text = text.split(value).join('');
    }
    return text;
  }
  function label(el) {
    const text = el.matches('input,textarea,select') ? '' : publicText(el);
    const associated = Array.from(el.labels || []).map(publicText).join(' ');
    const imageAlt = el.querySelector?.('img[alt]')?.getAttribute('alt');
    return (el.getAttribute('aria-label') || associated || text || imageAlt || el.getAttribute('placeholder') || el.getAttribute('title') || '').trim().slice(0,200);
  }
  function signature(el) {
    const ancestor = el.closest?.('a,form');
    return JSON.stringify([label(el), el.tagName, location.href,
      ...['href','type','role','name','formaction','onclick','aria-disabled'].map(a => el.getAttribute(a)),
      ancestor?.getAttribute('href'), ancestor?.getAttribute('action')]);
  }
  function findSearch() {
    function find(root) {
      for (const el of root.querySelectorAll('*')) {
        if (el.matches('input') && !el.disabled && !el.matches('input[type=password],input[type=hidden]') &&
            /search|खोज/i.test([el.getAttribute('type'), el.getAttribute('name'), el.getAttribute('id'), label(el)].join(' '))) {
          el.scrollIntoView({block:'center'});
          return label(el) || 'Search';
        }
        if (el.shadowRoot) { const found = find(el.shadowRoot); if (found) return found; }
      }
      return null;
    }
    return find(document);
  }
  function isSearch(el) {
    return /search|खोज/i.test([el.getAttribute('type'),el.getAttribute('name'),el.getAttribute('id'),label(el)].join(' ')) ||
      el.closest?.('form')?.getAttribute('role') === 'search';
  }
  function searchForm(el) {
    const form = el.form;
    if (!form || !isSearch(el)) return false;
    return Array.from(form.elements || []).filter(field => field.matches?.('input:not([type=hidden]),textarea,select'))
      .every(field => !sensitiveField(field) && (isSearch(field) || ['submit','button','checkbox','radio'].includes(field.type)));
  }
  function sensitiveField(el) {
    return /password|hidden|file/i.test(el.getAttribute('type') || '') ||
      /one-time-code|cc-|current-password|new-password/i.test(el.getAttribute('autocomplete') || '') ||
      /password|one.time|otp|card.?number|cvv|\bpin\b/i.test([label(el),el.getAttribute('name'),el.getAttribute('id')].join(' '));
  }
  function visible(el) {
    const r = el.getBoundingClientRect(), s = getComputedStyle(el);
    return r.width > 0 && r.height > 0 && r.top < innerHeight && r.left < innerWidth && r.bottom > 0 && r.right > 0 && s.visibility === 'visible' && s.display !== 'none';
  }
  function computeSelector(el) {
    try {
      if (el.id && /^[A-Za-z][A-Za-z0-9_-]*$/.test(el.id)) {
        return '#' + el.id;
      }
      const tag = (el.tagName || '').toLowerCase();
      const name = el.getAttribute ? el.getAttribute('name') : null;
      if (name) return tag + '[name="' + name + '"]';
      const type = el.getAttribute ? el.getAttribute('type') : null;
      if (type && ['submit', 'button', 'search', 'email', 'tel', 'number', 'text'].includes(type)) {
        const ariaLabel = el.getAttribute ? el.getAttribute('aria-label') : null;
        if (ariaLabel) return tag + '[type="' + type + '"][aria-label="' + ariaLabel + '"]';
        return tag + '[type="' + type + '"]';
      }
      const ariaLabel = el.getAttribute ? el.getAttribute('aria-label') : null;
      if (ariaLabel) return tag + '[aria-label="' + ariaLabel + '"]';
      const placeholder = el.getAttribute ? el.getAttribute('placeholder') : null;
      if (placeholder) return tag + '[placeholder="' + placeholder + '"]';
      return tag;
    } catch (e) {
      return '';
    }
  }
  function snapshot() {
    targets = new Map(); const nodes = []; let sensitive = false;
    notifyBridge();
    function walk(root) {
      observe(root);
      for (const el of root.querySelectorAll('*')) {
        if (el.tagName === 'IFRAME' || el.isContentEditable) sensitive = true;
        if (el.matches('input,textarea,select') && sensitiveField(el)) sensitive = true;
        if (el.shadowRoot) walk(el.shadowRoot);
        if (!visible(el) || !el.matches('a,button,input,select,textarea,[role=button],[role=link],h1,h2,h3,p,label,iframe')) continue;
        if (!ids.has(el)) ids.set(el, String(++nextId));
        const id = ids.get(el), r = el.getBoundingClientRect();
        const privateField = el.matches('input,textarea,select');
        const isSensitive = privateField && sensitiveField(el);
        if (el.isContentEditable) continue;
        const node = {
          id,
          selector: computeSelector(el),
          label: isSensitive ? '[REDACTED]' : (label(el) || (el.tagName === 'IFRAME' ? (el.title || el.id || 'Payment frame') : '')),
          tag: el.tagName,
          role: el.getAttribute('role') || '',
          type: el.getAttribute('type') || '',
          name: isSensitive ? '[REDACTED]' : (el.getAttribute('name') || ''),
          placeholder: isSensitive ? '[REDACTED]' : (el.getAttribute('placeholder') || ''),
          autocomplete: el.getAttribute('autocomplete') || '',
          href: el.tagName === 'A' ? el.href || '' : '',
          download: el.hasAttribute ? el.hasAttribute('download') : false,
          clickable: !isSensitive && el.matches('a,button,[role=button],[role=link],iframe'),
          search: !isSensitive && isSearch(el),
          search_form: !isSensitive && searchForm(el),
          sensitive: isSensitive,
          filled: privateField && !!el.value,
          disabled: !!el.disabled || el.getAttribute('aria-disabled') === 'true',
          editable: privateField && !isSensitive,
          x: r.x, y: r.y, width: r.width, height: r.height
        };
        if (isSensitive) node.value = '[REDACTED]';
        nodes.push(node);
        targets.set(id, {el, signature:signature(el), revision, value: privateField ? el.value : undefined});
      }
    }
    walk(document);
    return JSON.stringify({revision, url:location.href, title:document.title || '', site:location.hostname || '', scroll_y:window.scrollY || 0, width:innerWidth, height:innerHeight, sensitive, nodes: nodes.sort((a,b) => Number(b.editable) - Number(a.editable)).slice(0,150)});
  }
  function click(id, expectedRevision, requireUnchangedPage = true) {
    const t = targets.get(id);
    if (!t || t.revision !== expectedRevision || !t.el.isConnected || !visible(t.el) || t.el.disabled || signature(t.el) !== t.signature || t.el.getAttribute('aria-disabled') === 'true') return false;
    const risky = /\b(pay(?:ment)?|submit|confirm|transfer|delete|remove|proceed|checkout|otp|logout|book now|send|purchase|buy)\b/i.test(label(t.el));
    if (revision !== expectedRevision && (requireUnchangedPage || risky)) return false;
    if (t.el.matches('input,textarea,select')) return false;
    const r = t.el.getBoundingClientRect();
    const x = Math.max(0, r.left) + (Math.min(innerWidth,r.right)-Math.max(0,r.left))/2;
    const y = Math.max(0, r.top) + (Math.min(innerHeight,r.bottom)-Math.max(0,r.top))/2;
    let hit = document.elementFromPoint(x,y);
    while (hit && hit.shadowRoot) { const nested = hit.shadowRoot.elementFromPoint(x,y); if (!nested || nested === hit) break; hit = nested; }
    if (!hit || !(hit === t.el || t.el.contains(hit))) return false;
    t.el.click(); return true;
  }
  function enterOtp(code) {
    if (!code) return false;
    let target = null;
    for (const [id, t] of targets.entries()) {
      const el = t.el;
      if (!el.isConnected || !visible(el) || el.disabled || el.readOnly) continue;
      if (el.matches('input') && (/otp|one.?time|code|verification|passcode/i.test(label(el) + ' ' + el.name + ' ' + el.id + ' ' + (el.autocomplete||'')) || el.type === 'password' || el.type === 'number' || el.type === 'tel' || el.type === 'text')) {
        if (/otp|one.?time|code|verification|passcode/i.test(label(el) + ' ' + el.name + ' ' + el.id + ' ' + (el.autocomplete||''))) {
          target = el;
          break;
        }
        if (!target && (el.type === 'password' || el.autocomplete === 'one-time-code')) {
          target = el;
        }
      }
    }
    if (!target) {
      const active = document.activeElement;
      if (active && active.matches('input') && !active.disabled && !active.readOnly) {
        target = active;
      }
    }
    if (!target) {
      for (const el of document.querySelectorAll('input:not([type=hidden])')) {
        if (!visible(el) || el.disabled || el.readOnly) continue;
        if (/otp|one.?time|code|verification|passcode/i.test([el.name, el.id, el.placeholder, el.getAttribute('autocomplete'), label(el)].join(' '))) {
          target = el;
          break;
        }
      }
    }
    if (!target) return false;
    const proto = target.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    const setter = Object.getOwnPropertyDescriptor(proto, 'value')?.set;
    if (!setter) return false;
    target.focus();
    setter.call(target, code);
    target.dispatchEvent(new Event('input', {bubbles: true}));
    target.dispatchEvent(new Event('change', {bubbles: true}));
    return target.value === code;
  }
  function executeMacroStep(step) {
    if (!step) return false;
    const action = step.action;
    function findTarget(s) {
      if (s.selector && typeof document.querySelector === 'function') {
        try {
          const el = document.querySelector(s.selector);
          if (el && visible(el) && !el.disabled && el.getAttribute?.('aria-disabled') !== 'true') return el;
        } catch (e) {}
      }
      function searchIn(root) {
        if (!root || typeof root.querySelectorAll !== 'function') return null;
        for (const el of root.querySelectorAll('*')) {
          if (!visible(el) || el.disabled || el.getAttribute?.('aria-disabled') === 'true') continue;
          if (s.tag && el.tagName && el.tagName.toUpperCase() !== s.tag.toUpperCase()) continue;
          if (s.label && label(el).toLowerCase() === s.label.toLowerCase()) return el;
          if (s.name && el.getAttribute?.('name') === s.name) return el;
          if (s.placeholder && el.getAttribute?.('placeholder') === s.placeholder) return el;
          if (el.shadowRoot) {
            const nested = searchIn(el.shadowRoot);
            if (nested) return nested;
          }
        }
        return null;
      }
      return searchIn(document);
    }

    if (action === 'CLICK') {
      const el = findTarget(step);
      if (!el) return false;
      if (typeof el.scrollIntoView === 'function') el.scrollIntoView({block: 'center'});
      if (typeof el.click === 'function') el.click();
      return true;
    }
    if (action === 'TYPE') {
      const el = findTarget(step);
      if (!el || !el.matches?.('input,textarea')) return false;
      const proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
      const setter = Object.getOwnPropertyDescriptor(proto, 'value')?.set;
      if (!setter) return false;
      if (typeof el.focus === 'function') el.focus();
      setter.call(el, step.text || '');
      if (typeof el.dispatchEvent === 'function') {
        el.dispatchEvent(new Event('input', {bubbles: true}));
        el.dispatchEvent(new Event('change', {bubbles: true}));
      }
      return el.value === (step.text || '');
    }
    if (action === 'SUBMIT') {
      const el = findTarget(step);
      if (!el || !el.form || typeof el.form.requestSubmit !== 'function') return false;
      el.form.requestSubmit();
      return true;
    }
    return false;
  }
  function perform(action, expectedRevision, strict) {
    if (action.action === 'CLICK') return click(action.target_id, expectedRevision, strict);
    const t = targets.get(action.target_id);
    if (!t || t.revision !== expectedRevision || !t.el.isConnected || !visible(t.el) || t.el.disabled ||
        t.el.readOnly || t.el.value !== t.value || signature(t.el) !== t.signature || sensitiveField(t.el) ||
        t.el.getAttribute('aria-disabled') === 'true' || (strict && revision !== expectedRevision)) return false;
    const el = t.el, rect = el.getBoundingClientRect();
    let root = el.getRootNode ? el.getRootNode() : document;
    const hit = root.elementFromPoint(Math.max(0,rect.left) + Math.min(rect.width,innerWidth-Math.max(0,rect.left))/2,
      Math.max(0,rect.top) + Math.min(rect.height,innerHeight-Math.max(0,rect.top))/2);
    if (hit !== el && !el.contains(hit)) return false;
    if (action.action === 'TYPE') {
      if (!el.matches('input,textarea') || (el.tagName === 'INPUT' && !['text','search','email','tel','url','number',''].includes(el.getAttribute('type') || ''))) return false;
      const proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
      const setter = Object.getOwnPropertyDescriptor(proto, 'value')?.set;
      if (!setter) return false;
      el.focus(); setter.call(el,action.text);
      el.dispatchEvent(new Event('input',{bubbles:true})); el.dispatchEvent(new Event('change',{bubbles:true}));
      return el.value === action.text;
    }
    if (action.action === 'SUBMIT') {
      if (!el.matches('input,textarea') || !el.form || typeof el.form.requestSubmit !== 'function') return false;
      if (!el.form.checkValidity()) return false;
      el.form.requestSubmit(); return true;
    }
    return false;
  }
  window.__vc = {snapshot, click, findSearch, perform, enterOtp, executeMacroStep};
})();
