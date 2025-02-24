import { DBOS } from "@dbos-inc/dbos-sdk";
import { Workflows } from './Workflows.js';

export class InviteCustomer {

    @DBOS.workflow()
    static async inviteCustomer(inviteCustomerEvent: any): Promise<any> {

        const inviteCustomerResult = await Workflows.inviteCustomer(inviteCustomerEvent);

        if(inviteCustomerResult.type === "CustomerInvited") {
            Workflows.communicateToCustomer(inviteCustomerResult);
            
            const verifyCustomerResult = await Workflows.verifyCustomer(inviteCustomerResult);
            if(verifyCustomerResult.type === "CustomerVerified") {
                Workflows.communicateToCustomer(verifyCustomerResult);
            }
        }
        return inviteCustomerResult;

    }

}