(ns generate
  (:require ["mustache$default" :as m]
            ["fs" :refer [writeFileSync]]
            [clj.handlers :refer [handlers]]))

(def tmpl "import { DBOS } from \"@dbos-inc/dbos-sdk\";
import { handlers } from './clj.js'

export class {{name}} {
           
 {{#handlers}}
  {{#hasLookup}}
  @DBOS.transaction({readOnly: true})
  @DBOS.step()
  static {{hname}}Lookup(event: any): Promise<any> {
    const { {{hname}} } = handlers;
    return {{hname}}.lookup(event);
  }

  {{/hasLookup}}
  {{#hasWrite}}
  @DBOS.transaction({readOnly: false})
  static {{hname}}Write(event: any): Promise<any> {
    const { {{hname}} } = handlers;
    return {{hname}}.write(event);
  }

  {{/hasWrite}}
  @DBOS.workflow()
  static async {{hname}}(event: any): Promise<any> {
    const { {{hname}} } = handlers;
  {{#hasLookup}}
    const lookup = await Workflows.{{hname}}Lookup(event);
    DBOS.logger.info(`Completed lookup ${JSON.stringify(lookup)}`);
  {{/hasLookup}}
  {{^hasLookup}}
    const lookup = {};
  {{/hasLookup}}
    const newEvent = {{hname}}.handler(event,lookup);
    DBOS.logger.info(`Completed handler ${JSON.stringify(newEvent)}`);
  {{#hasWrite}}
    if (newEvent) {
      await Workflows.{{hname}}Write(newEvent);
      DBOS.logger.info(`Completed write ${JSON.stringify(newEvent)}`);
    }
  {{/hasWrite}}
    if (newEvent) {
      DBOS.setEvent(\"event\", newEvent);
    }
    return newEvent;
  }

{{/handlers}}           
}")

(let [wf-name "Workflows"
      handler-definitions {"name" wf-name
                           "handlers" (->> handlers
                                           (map (fn [[k v]]
                                                  {"hname" (name k)
                                                   "hasWrite" (boolean (:write v))
                                                   "hasLookup" (boolean (:lookup v))})))}]
  (writeFileSync (str "src/" wf-name ".ts")
                 (m/render tmpl (clj->js handler-definitions))))


