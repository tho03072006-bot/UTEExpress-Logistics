'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../../main/resources/static/js/delivery-proof.js'),'utf8');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
function element(){return {listeners:{},value:'',classList:{toggle(){}},addEventListener(type,fn){this.listeners[type]=fn;},setCustomValidity(v){this.error=v;},reportValidity(){},removeAttribute(){}};}
function setup(failCanvas=false) {
 const images=[],alerts=[],canvas=element(),form=element(),input=element(),preview=element(),signature=element(),clear=element();
 const context={fillRect(){},beginPath(){},moveTo(){},lineTo(){},stroke(){},closePath(){},drawImage(){if(failCanvas)throw new Error('Không xử lý được ảnh');}};
 Object.assign(canvas,{width:300,height:100,getContext(){return context;},getBoundingClientRect(){return {left:0,top:0,width:300,height:100};},setPointerCapture(){},hasPointerCapture(){return false;},toDataURL(){return 'data:image/png;base64,signature';}});
 input.files=[];
 const fields={signatureCanvas:canvas,deliveryProofForm:form,signatureData:signature,clearSignature:clear,proofImage:input,proofPreview:preview};
 class FakeImage{constructor(){this.naturalWidth=2000;this.naturalHeight=2000;images.push(this);}}
 class Transfer{constructor(){this.files=[];this.items={add:f=>this.files.push(f)};}}
 class File{constructor(parts,name,options){this.name=name;this.type=options.type;this.size=parts[0].size;}}
 vm.runInNewContext(source,{document:{getElementById(id){return fields[id];},createElement(){return {getContext(){return context;},toBlob(fn){fn({size:1000});}};}},
  window:{alert(m){alerts.push(m);},addEventListener(){}},URL:{createObjectURL(file){return 'blob:'+file.name;},revokeObjectURL(){}},Image:FakeImage,DataTransfer:Transfer,File,Date});
 const pointer=(type,x=10,y=10)=>canvas.listeners[type]({preventDefault(){},pointerId:1,clientX:x,clientY:y});
 const submit=()=>{let prevented=false;form.listeners.submit({preventDefault(){prevented=true;}});return prevented;};
 return {images,alerts,input,preview,signature,pointer,submit,choose(file){input.files=file?[file]:[];return input.listeners.change();}};
}
test('Chỉ chạm vào canvas chưa tạo nét ký thì không gửi bằng chứng',()=>{const q=setup();q.pointer('pointerdown');q.pointer('pointerup');assert.equal(q.submit(),true);assert.equal(q.signature.value,'');});
test('Nét ký thực có thể được gửi',()=>{const q=setup();q.pointer('pointerdown');q.pointer('pointermove',30,20);q.pointer('pointerup');assert.equal(q.submit(),false);assert.match(q.signature.value,/^data:image\/png/);});
test('Ảnh nén cũ không được ghi đè lựa chọn mới hơn',async()=>{
 const q=setup();const first={name:'old.png',type:'image/png',size:6e6},second={name:'latest.png',type:'image/png',size:50000};
 const a=q.choose(first);const b=q.choose(second);if(q.images[1])q.images[1].onload();await tick();
 q.images[0].onload();await Promise.all([a,b]);assert.match(q.input.files[0].name,/^latest\.(png|jpg)$/);assert.equal(q.preview.src,'blob:'+q.input.files[0].name);
});
test('Xóa lựa chọn khi ảnh cũ đang nén thì không phục hồi ảnh cũ',async()=>{
 const q=setup();const a=q.choose({name:'old.png',type:'image/png',size:6e6});await q.choose(null);q.images[0].onload();await a;
 assert.equal(q.input.files.length,0);assert.equal(q.preview.hidden,true);
});
test('Ảnh hỏng báo lỗi và chọn ảnh mới hợp lệ có thể phục hồi',async()=>{
 const q=setup();const a=q.choose({name:'broken.png',type:'image/png',size:1000});q.images[0].onerror();await a;
 assert.match(q.input.error,/Không đọc được ảnh/);assert.equal(q.preview.hidden,true);
 const b=q.choose({name:'valid.png',type:'image/png',size:1000});q.images[1].onload();await b;assert.equal(q.input.error,'');assert.equal(q.preview.hidden,false);
});
test('Lỗi canvas lúc nén không làm kẹt trạng thái đang xử lý',async()=>{
 const q=setup(true);const a=q.choose({name:'photo.png',type:'image/png',size:6e6});q.images[0].onload();await a;
 assert.match(q.input.error,/Không xử lý được ảnh/);q.pointer('pointerdown');q.pointer('pointermove',30,20);q.pointer('pointerup');q.submit();
 assert.equal(q.alerts.some(m=>m.includes('Đang thu nhỏ')),false);
});
test('Ảnh gốc quá lớn bị từ chối trước khi giải mã trên trình duyệt',async()=>{
 const q=setup();await q.choose({name:'huge.png',type:'image/png',size:26*1024*1024});assert.match(q.input.error,/25 MB/);assert.equal(q.images.length,0);assert.equal(q.preview.hidden,true);
});
