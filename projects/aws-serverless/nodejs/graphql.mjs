import { loadFile, addClassPath } from 'nbb';

addClassPath('src');

const {handler} = await loadFile('src/graphql.cljs');

console.log('handler js ', handler);
export {
    handler
};