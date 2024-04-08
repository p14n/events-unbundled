import { loadFile, addClassPath } from 'nbb';

addClassPath('src');

const x = await loadFile('src/lambda_handlers.cljs');
export const lambdas = x;

//await loadFile('src/test.cljs');

