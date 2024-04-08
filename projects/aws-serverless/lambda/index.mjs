import { loadFile, addClassPath } from 'nbb';

addClassPath('src');

const x = await loadFile('src/lambdas_handlers.cljs');

export const lambdas = x;