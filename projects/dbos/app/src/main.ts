import { ArgSource, ArgSources, DBOS } from "@dbos-inc/dbos-sdk";
import { send } from '@koa/send';
import { Workflows } from './Workflows.js';

export class AppServer {

  @DBOS.getApi('/customer/invite/:email')
  static async inviteCustomer(@ArgSource(ArgSources.URL) email: string) {
    const handle = await DBOS.startWorkflow(Workflows).inviteCustomer({type: "InviteCustomer", email: email});
    return await handle.getResult();
  }

  @DBOS.postApi('/crash')
  static async crash() {
    process.exit(1);
  }

  @DBOS.getApi('/')
  static async serve() {
      return send(DBOS.koaContext, "app.html", {root: 'html'}); // Adjust root to point to directory w/ files
  }

} 
