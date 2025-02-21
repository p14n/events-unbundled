import { loadFile } from 'nbb';

const x = await loadFile('src/lambda_handlers.cljs');
export const lambdas = x;
