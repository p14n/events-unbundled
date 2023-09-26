@system
Feature: Inviting a customer

  Scenario: Send an invite to a customer
    When I use the query
      """
      mutation {
       InviteCustomer(email:"dean@p14n.com"){
         id
         invited
       }
      }
      """
    Then the response value at [:data :InviteCustomer :invited] is
      """
      true
      """