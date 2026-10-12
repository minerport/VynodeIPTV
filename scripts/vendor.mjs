import {mkdir,copyFile} from 'node:fs/promises';
await mkdir('server/web/vendor',{recursive:true});
await copyFile('node_modules/hls.js/dist/hls.min.js','server/web/vendor/hls.min.js');
await copyFile('node_modules/hls.js/LICENSE','server/web/vendor/HLS-LICENSE.txt');
