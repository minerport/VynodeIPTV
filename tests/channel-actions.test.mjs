import test from 'node:test';
import assert from 'node:assert/strict';
import {safeChannelLogo} from '../server/web/channel-actions.js';
test('channel artwork rejects credentials and unsafe schemes',()=>{
 for(const v of ['http://example.com/a','https://u:p@example.com/a','https://example.com/a?token=x','data:image/svg+xml,bad','javascript:alert(1)','//example.com/a','https://example.com/a#f',null])assert.equal(safeChannelLogo(v),'');
 assert.equal(safeChannelLogo('https://example.com/a.png'),'https://example.com/a.png');
});
