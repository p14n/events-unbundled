import { loadFile,addClassPath } from 'nbb';

addClassPath('src');

const x = await loadFile('src/clj/handlers.cljs');
export const handlers = x;
