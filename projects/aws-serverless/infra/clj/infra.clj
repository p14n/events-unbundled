(ns infra
  (:require [cheshire.core :as json]
            [clojure.string :as str]))

(defn assoc-if [m k v]
  (if (and m k v)
    (assoc m k v)
    m))


(defn rule-name [topic]
  (str topic "_events"))

(defn table-policy [table-name]
  {"Version" "2012-10-17",
   "Statement" [{"Sid" "DynamoDBIndexAndStreamAccess",
                 "Effect" "Allow",
                 "Action" ["dynamodb:GetShardIterator",
                           "dynamodb:Scan",
                           "dynamodb:Query",
                           "dynamodb:DescribeStream",
                           "dynamodb:GetRecords",
                           "dynamodb:ListStreams"],
                 "Resource" [(str "arn:aws:dynamodb:*:*:table/" table-name "/index/*")
                             (str "arn:aws:dynamodb:*:*:table/" table-name "/stream/*")]},
                {"Sid" "DynamoDBTableAccess",
                 "Effect" "Allow",
                 "Action" ["dynamodb:BatchGetItem",
                           "dynamodb:BatchWriteItem",
                           "dynamodb:ConditionCheckItem",
                           "dynamodb:PutItem",
                           "dynamodb:DescribeTable",
                           "dynamodb:DeleteItem",
                           "dynamodb:GetItem",
                           "dynamodb:Scan",
                           "dynamodb:Query",
                           "dynamodb:UpdateItem"],
                 "Resource" (str "arn:aws:dynamodb:*:*:table/" table-name)},
                {"Sid" "DynamoDBDescribeLimitsAccess",
                 "Effect" "Allow",
                 "Action" "dynamodb:DescribeLimits",
                 "Resource" [(str "arn:aws:dynamodb:*:*:table/" table-name),
                             (str "arn:aws:dynamodb:*:*:table/" table-name "/index/*")]}]})

(def pipe-policy
  {"Version" "2012-10-17"
   "Statement" [{"Action" ["dynamodb:DescribeStream"
                           "dynamodb:GetRecords"
                           "dynamodb:GetShardIterator"
                           "dynamodb:ListStreams"
                           "events:PutEvents"]
                 "Effect" "Allow"
                 "Resource" "*"}]})

(def elasticache-policy
  {"Statement" [{"Action" ["elasticache:Connect"],
                 "Effect" "Allow",
                 "Resource" ["*"]}],
   "Version" "2012-10-17"})

(def pipe-role-policy
  {"Version" "2012-10-17"
   "Statement" [{"Action" "sts:AssumeRole"
                 "Effect" "Allow"
                 "Principal" {"Service" "pipes.amazonaws.com"}}]})

(defn synth-lambda
  ([name]
   (synth-lambda name (str "index.lambdas." name) "../lambda/lambda.zip"))
  ([name handler package]
   [(str "lambda_function_" name) [{"create_package" false,
                                    "description" (str "Lambda function " name),
                                    "function_name" name,
                                    "handler" handler,
                                    "local_existing_package" package,
                                    "runtime" "nodejs20.x",
                                    "attach_network_policy" true,
                                    "layers" ["${aws_lambda_layer_version.lambda_layer.arn}"]
                                    "timeout" 10
                                    "vpc_subnet_ids" "${data.aws_subnets.main.ids}",
                                    "vpc_security_group_ids" ["${aws_security_group.elasticache_bidirectional.id}"],
                                    "source" "terraform-aws-modules/lambda/aws"}]]))

(defn synth-lambda-target [rule-name handler]
  {"arn" (str "${module.lambda_function_" handler ".lambda_function_arn}"),
   "name" (str "send-" rule-name "-to-" handler)})

(defn synth-lambda-rule-permission [rule-name handler]
  [(str rule-name "_" handler)
   [{"action" "lambda:InvokeFunction",
     "function_name" (str "${module.lambda_function_" handler ".lambda_function_name}"),
     "principal" "events.amazonaws.com",
     "source_arn" (str "${module.eventbridge.eventbridge_rule_arns[\"" rule-name "\"]}")}]])

(defn synth-lambda-rule-permissions [grouped-by-topic]
  (->> grouped-by-topic
       (mapv (fn [[topic handlers]]
               (->> handlers
                    (mapv (partial synth-lambda-rule-permission (rule-name topic)))
                    (into {}))))
       (apply merge)))


(defn handler-metas [handlers]
  (->> handlers
       (map meta)
       (map #(assoc % :flat-name (-> % :name name
                                     (str/replace "-" "")
                                     (str/replace "_" ""))))))

(defn group-by-topic [handler-metas]
  (let [all-topics (->> handler-metas
                        (mapcat :in)
                        (set))]
    (->> all-topics
         (map (fn [topic]
                [(name topic) (->> handler-metas
                                   (filter #(some #{topic} (:in %)))
                                   (mapv :flat-name))]))
         (into {}))))


(defn create-rules [grouped-by-topic]
  (->> grouped-by-topic
       keys
       (map (fn [topic]
              [(rule-name topic)
               {"description" (str "Capture all " topic " events"),
                "enabled" true,
                "event_pattern" (json/generate-string {"detail" {"topic" [topic]}})}]))
       (into {})))

(defn create-targets [grouped-by-topic]
  (->> grouped-by-topic
       (map (fn [[topic handlers]]
              [(rule-name topic)
               (mapv (partial synth-lambda-target (rule-name topic)) handlers)]))
       (into {})))


(defn create-dynamodb-table
  ([table-name attributes streaming hash]
   (create-dynamodb-table table-name attributes streaming hash nil))
  ([table-name attributes streaming hash range]
   (-> {"attributes" (->> attributes
                          (mapv (fn [[k v]] {"name" k, "type" v}))) ,
        "hash_key" hash,
        "name" table-name,
        "source" "terraform-aws-modules/dynamodb-table/aws"}
       (assoc-if "range_key" range)
       (assoc-if "stream_enabled" (when streaming true))
       (assoc-if "stream_view_type" streaming))))



(defn- ddb-handler-policy-attachments [table-name all-handler-names]
  (->> all-handler-names
       (map (fn [handler-name]
              [(str "ddb_" table-name "_pa_" handler-name)
               [{"policy_arn" (str "${aws_iam_policy.ddb_" table-name "_policy.arn}"),
                 "role" (str "${module.lambda_function_" handler-name ".lambda_role_name}")}]]))
       (into {})))

(defn- elasticache-policy-attachments [all-handler-names]
  (->> all-handler-names
       (map (fn [handler-name]
              [(str "elasticache_pa_" handler-name)
               [{"policy_arn" (str "${aws_iam_policy.elasticache_policy.arn}"),
                 "role" (str "${module.lambda_function_" handler-name ".lambda_role_name}")}]]))
       (into {})))

(defn ddb-policies [table-names]
  (->> table-names
       (map (fn [table-name]
              [(str "ddb_" table-name "_policy")
               [{"name" (str "ddb_" table-name "_policy"),
                 "policy" (json/generate-string (table-policy table-name))}]]))
       (into {})))


(defn- create-api-gateway []
  {"aws_api_gateway_deployment" {"graphql" [{"depends_on" ["aws_api_gateway_integration.lambda"
                                                           "aws_api_gateway_integration.lambda_root"],
                                             "rest_api_id" "${aws_api_gateway_rest_api.graphql.id}",
                                             "stage_name" "live"}]},
   "aws_api_gateway_integration" {"lambda" [{"http_method" "${aws_api_gateway_method.proxy.http_method}",
                                             "integration_http_method" "POST",
                                             "resource_id" "${aws_api_gateway_method.proxy.resource_id}",
                                             "rest_api_id" "${aws_api_gateway_rest_api.graphql.id}",
                                             "type" "AWS_PROXY",
                                             "uri" "${module.lambda_function_graphql.lambda_function_invoke_arn}"}],
                                  "lambda_root" [{"http_method" "${aws_api_gateway_method.proxy_root.http_method}",
                                                  "integration_http_method" "POST",
                                                  "resource_id" "${aws_api_gateway_method.proxy_root.resource_id}",
                                                  "rest_api_id" "${aws_api_gateway_rest_api.graphql.id}",
                                                  "type" "AWS_PROXY",
                                                  "uri" "${module.lambda_function_graphql.lambda_function_invoke_arn}"}]},
   "aws_api_gateway_method" {"proxy" [{"authorization" "NONE",
                                       "http_method" "ANY",
                                       "resource_id" "${aws_api_gateway_resource.proxy.id}",
                                       "rest_api_id" "${aws_api_gateway_rest_api.graphql.id}"}],
                             "proxy_root" [{"authorization" "NONE",
                                            "http_method" "ANY",
                                            "resource_id" "${aws_api_gateway_rest_api.graphql.root_resource_id}",
                                            "rest_api_id" "${aws_api_gateway_rest_api.graphql.id}"}]},
   "aws_api_gateway_resource" {"proxy" [{"parent_id" "${aws_api_gateway_rest_api.graphql.root_resource_id}",
                                         "path_part" "{proxy+}",
                                         "rest_api_id" "${aws_api_gateway_rest_api.graphql.id}"}]},
   "aws_api_gateway_rest_api" {"graphql" [{"description" "Graphql API",
                                           "name" "Graphql"}]}})

(defn create-dynamodb-tables []
  {"dynamodb_table_events" (create-dynamodb-table
                            "events"
                            {"event-id" "S" "created" "S"}
                            "NEW_IMAGE" "event-id" "created")
   "dynamodb_table_customer_emails" (create-dynamodb-table
                                     "customer_emails"
                                     {"email" "S"}
                                     nil "email")
   "dynamodb_table_customers" (create-dynamodb-table
                               "customers"
                               {"id" "S"}
                               nil "id")})


(defn synth
  ([hs]
   (let [handlers (handler-metas hs)
         grouped-by-topic (group-by-topic handlers)
         all-handler-names (map :flat-name handlers)]
     {"module" (apply merge
                      [(create-dynamodb-tables)
                       {"eventbridge" [{"bus_name" "eventbridge-producer-events",
                                        "rules" (create-rules grouped-by-topic)
                                        "targets" (create-targets grouped-by-topic)
                                        "source" "terraform-aws-modules/eventbridge/aws"}]}
                       (into {} (concat (map synth-lambda all-handler-names)
                                        [(synth-lambda "graphql" "graphql.handler" "../lambda/lambda.zip")]))])
      "resource" (merge
                  {"aws_lambda_layer_version" {"lambda_layer" [{"compatible_runtimes" ["nodejs20.x"],
                                                                "filename" "../layer.zip",
                                                                "layer_name" "events_unbundled"}]}
                   "aws_vpc_endpoint" {"dynamodb"  {"service_name" "com.amazonaws.eu-west-1.dynamodb",
                                                    "vpc_id" "${data.aws_vpc.main.id}"
                                                    "route_table_ids" ["${data.aws_vpc.main.main_route_table_id}"]}},
                   "aws_elasticache_serverless_cache" {"response_cache" [{"cache_usage_limits" [{"data_storage" [{"maximum" 1,
                                                                                                                  "unit" "GB"}],
                                                                                                 "ecpu_per_second" [{"maximum" 1000}]}],
                                                                          "description" "Response queues",
                                                                          "engine" "redis",
                                                                          "name" "response-queues"
                                                                          "subnet_ids" "${data.aws_subnets.main.ids}" ,
                                                                          "security_group_ids" ["${aws_security_group.elasticache_bidirectional.id}"]}]}
                   "aws_iam_policy" (merge {"pipe_policy" [{"name" "pipe-policy",
                                                            "policy" (json/generate-string pipe-policy)}]
                                            "elasticache_policy" [{"name" "elasticache-policy",
                                                                   "policy" (json/generate-string elasticache-policy)}]}
                                           (ddb-policies ["events" "customer_emails" "customers"])),
                   "aws_iam_role" {"pipe_role" [{"assume_role_policy" (json/generate-string pipe-role-policy)
                                                 "description" "Role for pipe",
                                                 "name" "pipe-role"}]},
                   "aws_iam_role_policy_attachment" (apply merge
                                                           [{"pipe_policy_attachment" [{"policy_arn" "${aws_iam_policy.pipe_policy.arn}",
                                                                                        "role" "${aws_iam_role.pipe_role.name}"}]}
                                                            (elasticache-policy-attachments (conj all-handler-names "graphql"))
                                                            (ddb-handler-policy-attachments "events" (conj all-handler-names "graphql"))
                                                            (ddb-handler-policy-attachments "customer_emails" ["invitecustomereventhandler"])
                                                            (ddb-handler-policy-attachments "customers" ["customerprojector"])]),
                   "aws_lambda_permission" (merge
                                            {"apigw" [{"action" "lambda:InvokeFunction",
                                                       "function_name" "${module.lambda_function_graphql.lambda_function_name}",
                                                       "principal" "apigateway.amazonaws.com",
                                                       "source_arn" "${aws_api_gateway_rest_api.graphql.execution_arn}/*/*/*",
                                                       "statement_id" "AllowAPIGatewayInvoke"}]}
                                            (synth-lambda-rule-permissions grouped-by-topic)),
                   "aws_security_group" {"elasticache_bidirectional"
                                         [{"description" "Allow traffic to and from elasticache",
                                           "name" "elasticache_bidirectional",
                                           "vpc_id" "${data.aws_vpc.main.id}"}]},
                   "aws_vpc_security_group_ingress_rule" {"elasticache_ingress"
                                                          [{"cidr_ipv4" "${data.aws_vpc.main.cidr_block}",
                                                            "from_port" "${aws_elasticache_serverless_cache.response_cache.endpoint[0].port}",
                                                            "ip_protocol" "tcp",
                                                            "security_group_id" "${aws_security_group.elasticache_bidirectional.id}",
                                                            "to_port" "${aws_elasticache_serverless_cache.response_cache.endpoint[0].port}"}]}
                   "aws_vpc_security_group_egress_rule" {"elasticache_egress"
                                                         [{"cidr_ipv4" "${data.aws_vpc.main.cidr_block}",
                                                           "from_port" "${aws_elasticache_serverless_cache.response_cache.endpoint[0].port}",
                                                           "ip_protocol" "tcp",
                                                           "security_group_id" "${aws_security_group.elasticache_bidirectional.id}",
                                                           "to_port" "${aws_elasticache_serverless_cache.response_cache.endpoint[0].port}"}]
                                                         "dynamodb_egress"
                                                         [{"cidr_ipv4" "0.0.0.0/0",
                                                           "from_port" 443,
                                                           "ip_protocol" "tcp",
                                                           "security_group_id" "${aws_security_group.elasticache_bidirectional.id}",
                                                           "to_port" 443}]}
                   "aws_pipes_pipe" {"events_pipe" [{"name" "events-pipe",
                                                     "role_arn" "${aws_iam_role.pipe_role.arn}",
                                                     "source" "${module.dynamodb_table_events.dynamodb_table_stream_arn}",
                                                     "source_parameters" [{"dynamodb_stream_parameters" [{"batch_size" 1,
                                                                                                          "starting_position" "LATEST"}]}],
                                                     "target" "${module.eventbridge.eventbridge_bus_arn}"
                                                     "target_parameters" {"input_template" (json/generate-string {"event-id" "<$.dynamodb.NewImage.event-id.S>",
                                                                                                                  "correlation-id" "<$.dynamodb.NewImage.correlation-id.S>",
                                                                                                                  "topic" "<$.dynamodb.NewImage.topic.S>",
                                                                                                                  "type" "<$.dynamodb.NewImage.type.S>",
                                                                                                                  "created" "<$.dynamodb.NewImage.created.S>",
                                                                                                                  "body" "<$.dynamodb.NewImage.body.S>"})}}]}}
                  (create-api-gateway))
      "data" {"aws_vpc" {"main" {"id" "${var.vpc_id}"}}
              "aws_subnets" {"main" {"filter" [{"name" "vpc-id",
                                                "values" ["${data.aws_vpc.main.id}"]}]}}}
      "provider" {"aws" [{"default_tags" [{"tags" {"Component" "aws-serverless"}}]
                          "region" "eu-west-1"}]}
      "variable" {"vpc_id" {}}
      "output" {"api-gateway-url" {"value" "${aws_api_gateway_deployment.graphql.invoke_url}"}
                "elasticache_endpoint" {"value" "${aws_elasticache_serverless_cache.response_cache.endpoint}"}}})))
