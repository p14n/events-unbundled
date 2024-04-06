import { loadFile, addClassPath } from 'nbb';

addClassPath('src'); // This is necessary when you require another .cljs file

const { inviteCustomer } = await loadFile('src/shell.cljs');

export { 
    inviteCustomer
};