(ns infra
  (:require [cheshire.core :as json]
            [clojure.string :as str]))


(defn rule-name [topic]
  (str topic "_events"))

(def pipe-policy
  {"Version" "2012-10-17"
   "Statement" [{"Action" ["dynamodb:DescribeStream"
                           "dynamodb:GetRecords"
                           "dynamodb:GetShardIterator"
                           "dynamodb:ListStreams"
                           "events:PutEvents"]
                 "Effect" "Allow"
                 "Resource" "*"}]})

(def pipe-role-policy
  {"Version" "2012-10-17"
   "Statement" [{"Action" "sts:AssumeRole"
                 "Effect" "Allow"
                 "Principal" {"Service" "pipes.amazonaws.com"}}]})

(defn synth-lambda [name]
  [(str "lambda_function_" name) [{"create_package" false,
                                   "description" (str "Lambda function " name),
                                   "function_name" name,
                                   "handler" (str "index.lambdas." name),
                                   "local_existing_package" "../lambda/lambda.zip",
                                   "runtime" "nodejs20.x",
                                   "source" "terraform-aws-modules/lambda/aws"}]])

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

(defn synth
  ([hs]
   (let [handlers (handler-metas hs)
         grouped-by-topic (group-by-topic handlers)]
     {"module" (merge
                {"dynamodb_table_events" [{"attributes" [{"name" "event-id", "type" "S"}
                                                         {"name" "created", "type" "S"}],
                                           "hash_key" "event-id",
                                           "range_key" "created"
                                           "name" "events",
                                           "stream_enabled" true
                                           "stream_view_type" "NEW_IMAGE"
                                           "source" "terraform-aws-modules/dynamodb-table/aws"}]
                 "eventbridge" [{"bus_name" "eventbridge-producer-events",
                                 "rules" (create-rules grouped-by-topic)
                                 "targets" (create-targets grouped-by-topic)
                                 "source" "terraform-aws-modules/eventbridge/aws"}]}
                (into {} (map synth-lambda (map :flat-name handlers))))
      "resource" {"aws_iam_policy" {"pipe_policy" [{"name" "pipe-policy",
                                                    "policy" (json/generate-string pipe-policy)}]},
                  "aws_iam_role" {"pipe_role" [{"assume_role_policy" (json/generate-string pipe-role-policy)
                                                "description" "Role for pipe",
                                                "name" "pipe-role"}]},
                  "aws_iam_role_policy_attachment" {"pipe_policy_attachment" [{"policy_arn" "${aws_iam_policy.pipe_policy.arn}",
                                                                               "role" "${aws_iam_role.pipe_role.name}"}]},
                  "aws_lambda_permission" (synth-lambda-rule-permissions grouped-by-topic),
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
      "provider" {"aws" [{"default_tags" [{"tags" {"Component" "aws-serverless"}}]
                          "region" "eu-west-1"}]}})))

