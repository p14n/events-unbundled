import { ArgSource, ArgSources, DBOS } from "@dbos-inc/dbos-sdk";
import { InviteCustomer } from './InviteCustomer.js';
export class AppServer {

  @DBOS.getApi('/customer/invite/:email')
  static async inviteCustomer(@ArgSource(ArgSources.URL) email: string) {
    const handle = await DBOS.startWorkflow(InviteCustomer).inviteCustomer({type: "InviteCustomer", email: email});
    return await handle.getResult();
  }

} 
