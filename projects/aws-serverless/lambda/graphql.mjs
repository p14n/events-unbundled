import { loadFile, addClassPath } from 'nbb';

addClassPath('src');

const {handler} = await loadFile('src/graphql.cljs');
export {
    handler
};

//await loadFile('src/test.cljs');

