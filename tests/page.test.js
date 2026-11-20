const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
let mutation, hit, clicks = 0;
class Element {
  constructor(label, editable = false) { this.innerText = label; this.editable = editable; this.isConnected = true; this.tagName = editable ? 'TEXTAREA' : 'BUTTON'; }
  getAttribute(name) { return this.attributes?.[name] || null; }
  matches(selector) {
    if (selector.includes('type=password')) return false;
    if (selector === 'input') return this.tagName === 'INPUT';
    if (selector === 'input,textarea,select') return this.editable;
    return true;
  }
  getBoundingClientRect() { return {x:10,y:10,left:10,top:10,right:100,bottom:60,width:90,height:50}; }
  scrollIntoView() { this.scrolled = true; }
  contains(el) { return el === this; }
  click() { clicks++; }
}
Element.prototype.attachShadow = function(options) { return options; };
const button = new Element('View product'), field = new Element('private textarea value', true);
const document = {querySelectorAll:() => [button, field], elementFromPoint:() => hit};
const context = {Element, document, window:{}, location:{href:'https://example.com'}, innerWidth:400, innerHeight:800,
  MutationObserver: class { constructor(fn) { mutation = fn; } observe() {} },
  getComputedStyle:() => ({visibility:'visible',display:'block'})};
vm.runInNewContext(fs.readFileSync('app/src/main/assets/page.js','utf8'), context);
const api = context.window.__vc;
let snapshot = JSON.parse(api.snapshot());
const targetId = snapshot.nodes.find(n => n.tag === 'BUTTON').id;
const fieldId = snapshot.nodes.find(n => n.tag === 'TEXTAREA').id;
assert.equal(snapshot.nodes.find(n => n.id === fieldId).label, '');
hit = button;
assert.equal(api.click(targetId, snapshot.revision, false), true);
assert.equal(clicks, 1);
assert.equal(api.click(fieldId, snapshot.revision), false);
hit = new Element('overlay');
assert.equal(api.click(targetId, snapshot.revision, false), false);
hit = button;
mutation(); // An unrelated banner mutation should not invalidate an unchanged target.
assert.equal(api.click(targetId, snapshot.revision, false), true);
button.attributes = {href:'/different-item'};
assert.equal(api.click(targetId, snapshot.revision, false), false);
button.attributes = {};
button.isConnected = false;
assert.equal(api.click(targetId, snapshot.revision, false), false);
button.isConnected = true;
button.innerText = 'Different product';
assert.equal(api.click(targetId, snapshot.revision, false), false);
button.innerText = 'View product';
snapshot = JSON.parse(api.snapshot());
button.disabled = true;
assert.equal(api.click(targetId, snapshot.revision, false), false);
button.disabled = false; button.innerText = 'Pay now';
snapshot = JSON.parse(api.snapshot()); mutation();
assert.equal(api.click(targetId, snapshot.revision, false), false); // Payments still require an unchanged page.
field.tagName = 'INPUT'; field.attributes = {type:'search', placeholder:'Search Amazon'};
assert.equal(api.findSearch(), 'Search Amazon');
assert.equal(field.scrolled, true);
snapshot = JSON.parse(api.snapshot());
assert.equal(snapshot.nodes.find(n => n.id === fieldId).type, 'search');
assert.equal(snapshot.nodes.find(n => n.id === fieldId).label, 'Search Amazon');

// Verify Macro Step Execution
const previousClicks = clicks;
assert.equal(api.executeMacroStep({action: 'CLICK', label: 'Pay now', tag: 'BUTTON'}), true);
assert.equal(clicks, previousClicks + 1);
assert.equal(api.executeMacroStep({action: 'CLICK', label: 'Non-existent button', tag: 'BUTTON'}), false);
assert.equal(api.executeMacroStep(null), false);

console.log('Page extraction, search discovery, click validation, and macro execution tests passed');
