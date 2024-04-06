import { loadFile, addClassPath } from 'nbb';

addClassPath('src'); // This is necessary when you require another .cljs file

const x = await loadFile('src/lambdas.cljs');

export const lambdas = x;