import { ArgSource, ArgSources, DBOS } from "@dbos-inc/dbos-sdk";
import { handlers } from './clj.js'
import { send } from '@koa/send';

export class MyApp {

  @DBOS.transaction({readOnly: true})
  @DBOS.step()
  static inviteCustomerLookup(event: any): Promise<any> {
    const { inviteCustomer } = handlers;
    return inviteCustomer.lookup(event);
  }

  @DBOS.transaction({readOnly: false})
  @DBOS.step()
  static inviteCustomerWrite(event: any): Promise<any> {
    const { inviteCustomer } = handlers;
    return inviteCustomer.write(event);
  }

  @DBOS.workflow()
  static async inviteCustomer(event: any): Promise<void> {
    const { inviteCustomer } = handlers;
    const lookup = inviteCustomer.lookup ? await MyApp.inviteCustomerLookup(event) : {};
    DBOS.logger.info(`Completed lookup ${JSON.stringify(lookup)}`);
    const newEvent = inviteCustomer.handler(event,lookup);
    DBOS.logger.info(`Completed write ${JSON.stringify(newEvent)}`);
    await (inviteCustomer.write && newEvent ? MyApp.inviteCustomerWrite(newEvent) : Promise.resolve());
    DBOS.logger.info(`Completed write ${JSON.stringify(newEvent)}`);
    if (newEvent) await DBOS.setEvent("event", newEvent);
  }
}

export class AppServer {

  @DBOS.getApi('/background/:taskid/:steps')
  static async inviteCustomerRoute(@ArgSource(ArgSources.URL) taskid: string) {
    await DBOS.startWorkflow(MyApp, { workflowID: taskid }).inviteCustomer({type: "InviteCustomer", email: "test@test.com"});
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
