const fs = require('node:fs'), vm = require('node:vm'), assert = require('node:assert/strict');
for (const site of ['library.example','news.example']) {
  let submitted = 0, events = [], overlay = false, reportedHostname = '';
  class Input {
    constructor() { this.tagName='INPUT'; this.isConnected=true; this.attributes={type:'search',name:'query',placeholder:'Search this site'}; this.innerText=''; this._value=''; }
    get value() { return this._value; } set value(value) { this._value=value; }
    getAttribute(key) { return this.attributes[key] || null; }
    matches(selector) {
      if (selector.includes('type=password')) return false;
      if (selector.includes('input:not')) return true;
      return selector.includes('input');
    }
    closest() { return this.form; }
    getBoundingClientRect() { return {x:0,y:0,left:0,top:0,right:100,bottom:50,width:100,height:50}; }
    contains(hit) { return hit === this; }
    focus() {} dispatchEvent(event) { events.push(event.type); }
  }
  Input.prototype.attachShadow = function() {};
  const input = new Input(), form={getAttribute:()=>null,elements:[input],checkValidity:()=>true,requestSubmit:()=>submitted++}; input.form=form;
  const document = {title:'Example page',querySelectorAll:()=>[input],elementFromPoint:()=>overlay ? {} : input};
  const AndroidVoiceBridge = {
    setHostname: (h) => { reportedHostname = h; }
  };
  const context = {Element:Input,HTMLInputElement:Input,HTMLTextAreaElement:class {},Event:class {constructor(type){this.type=type;}},
    document,window:{AndroidVoiceBridge},location:{href:`https://${site}/`,hostname:site},innerWidth:400,innerHeight:800,
    MutationObserver:class {observe(){}},getComputedStyle:()=>({visibility:'visible',display:'block'})};
  vm.runInNewContext(fs.readFileSync('app/src/main/assets/page.js','utf8'),context);
  assert.equal(reportedHostname, site);
  const api=context.window.__vc; let snapshot=JSON.parse(api.snapshot()); const id=snapshot.nodes[0].id;
  assert.equal(api.perform({action:'TYPE',target_id:id,text:'bird migration'},snapshot.revision,false),true);
  assert.equal(input.value,'bird migration'); assert.deepEqual(events,['input','change','blur']);
  snapshot=JSON.parse(api.snapshot()); assert.equal(snapshot.nodes[0].filled,true); assert.equal(snapshot.nodes[0].value,undefined);
  assert.equal(api.perform({action:'SUBMIT',target_id:id},snapshot.revision,false),true); assert.equal(submitted,1);
  overlay=true; assert.equal(api.perform({action:'TYPE',target_id:id,text:'other'},snapshot.revision,false),false); overlay=false;
  input.attributes.autocomplete='one-time-code'; snapshot=JSON.parse(api.snapshot()); assert.equal(snapshot.sensitive,true);
  assert.equal(snapshot.nodes[0].sensitive,true);
  assert.equal(snapshot.nodes[0].placeholder,'[REDACTED]');
  assert.equal(snapshot.nodes[0].value,'[REDACTED]');
  assert.equal(api.perform({action:'TYPE',target_id:id,text:'123456'},snapshot.revision,false),false);
  input.attributes.autocomplete=''; input.attributes.type='password'; input.value='do-not-upload';
  snapshot=JSON.parse(api.snapshot()); assert.equal(snapshot.sensitive,true);
  assert.equal(snapshot.nodes[0].sensitive,true);
  assert.equal(snapshot.nodes[0].placeholder,'[REDACTED]');
  assert.equal(snapshot.nodes[0].value,'[REDACTED]');
  assert.equal(JSON.stringify(snapshot).includes('do-not-upload'),false);
}
console.log('Generic typing, form submission, redaction and blocked-field tests passed for two site fixtures');
