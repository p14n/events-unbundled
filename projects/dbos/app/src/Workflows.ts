import { DBOS } from "@dbos-inc/dbos-sdk";
import { handlers } from './clj.js'

export class Workflows {

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
  static async inviteCustomer(event: any): Promise<any> {
    const { inviteCustomer } = handlers;
    const lookup = await Workflows.inviteCustomerLookup(event);
    DBOS.logger.info(`Completed lookup ${JSON.stringify(lookup)}`);
    const newEvent = inviteCustomer.handler(event,lookup);
    DBOS.logger.info(`Completed handler ${JSON.stringify(newEvent)}`);
    await newEvent ? Workflows.inviteCustomerWrite(newEvent) : Promise.resolve();
    DBOS.logger.info(`Completed write ${JSON.stringify(newEvent)}`);
    if (newEvent) {
      //DBOS.setEvent("event", newEvent);
      return newEvent;
    }
    const notify = await DBOS.recv("notify",1);
    DBOS.logger.info(`Completed notify ${JSON.stringify(notify)}`);
    return notify;
  }
}