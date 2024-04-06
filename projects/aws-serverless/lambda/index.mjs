import { loadFile, addClassPath } from 'nbb';

addClassPath('.'); // This is necessary when you require another .cljs file

const { inviteCustomer } = await loadFile('./shell.cljs');

export { 
    inviteCustomer
};