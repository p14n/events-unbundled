import { ArgSource, ArgSources, DBOS } from "@dbos-inc/dbos-sdk";
import { send } from '@koa/send';
import { Workflows } from './Workflows.js'

export class AppServer {

  @DBOS.getApi('/background/:taskid/:steps')
  static async inviteCustomerRoute(@ArgSource(ArgSources.URL) taskid: string) {
    await DBOS.startWorkflow(Workflows, { workflowID: taskid }).inviteCustomer({type: "InviteCustomer", email: "test@test.com"});
    return "Task launched!";
  }


  @DBOS.getApi('/last_step/:taskid')
  static async getTask(@ArgSource(ArgSources.URL) taskid: string) {
    return 10;
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
