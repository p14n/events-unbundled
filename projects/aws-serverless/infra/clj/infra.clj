(ns infra
  (:require [clojure.string :as str]
            [cheshire.core :as json]))



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
  (let [lc (str/lower-case name)]
    [(str "lambda_function_" lc) [{"create_package" false,
                                   "description" (str "Lambda function " name),
                                   "function_name" name,
                                   "handler" (str "index.lambdas." name),
                                   "local_existing_package" "../lambda/lambda.zip",
                                   "runtime" "nodejs20.x",
                                   "source" "terraform-aws-modules/lambda/aws"}]]))

(defn synth []
  {"module" (merge
             {"dynamodb_table_events" [{"attributes" [{"name" "id", "type" "S"}
                                                      {"name" "created", "type" "S"}
                                                      ;{"name" "topic", "type" "S"}
                                                      ;{"name" "type", "type" "S"}
                                                      ;{"name" "body", "type" "S"}
                                                      ],
                                        "hash_key" "id",
                                        "range_key" "created"
                                        "name" "events",
                                        "stream_enabled" true
                                        "stream_view_type" "NEW_IMAGE"
                                        "source" "terraform-aws-modules/dynamodb-table/aws"}]
              "eventbridge" [{"bus_name" "eventbridge-producer-events",
                              "rules" {"all_events" {"description" "Capture all events",
                                                     "enabled" true,
                                                     "event_pattern" "${jsonencode({ \"source\" : [\"Pipe events-pipe\"] })}"}}
                              "targets" {"all_events" [{"arn" "${module.lambda_function_invitecustomer.lambda_function_arn}",
                                                        "name" "send-events-to-lambda"}]},
                              "source" "terraform-aws-modules/eventbridge/aws"}]}
             (into {} (map synth-lambda ["inviteCustomer"])))
   "resource" {"aws_iam_policy" {"pipe_policy" [{"name" "pipe-policy",
                                                 "policy" (json/generate-string pipe-policy);"${jsonencode({\n    Version = \"2012-10-17\"\n    Statement = [\n      {\n        Action = [\n          \"dynamodb:DescribeStream\",\n          \"dynamodb:GetRecords\",\n          \"dynamodb:GetShardIterator\",\n          \"dynamodb:ListStreams\",\n          \"events:PutEvents\"\n        ]\n        Effect   = \"Allow\"\n        Resource = \"*\"\n      },\n    ]\n  })}"
                                                 }]},
               "aws_iam_role" {"pipe_role" [{"assume_role_policy" (json/generate-string pipe-role-policy)
                                              ;"${jsonencode({\n    Version = \"2012-10-17\"\n    Statement = [\n      {\n        Action = \"sts:AssumeRole\"\n        Effect = \"Allow\"\n        Sid    = \"\"\n        Principal = {\n          Service = \"pipes.amazonaws.com\"\n        }\n      },\n    ]\n  })}",
                                             "description" "Role for pipe",
                                             "name" "pipe-role"}]},
               "aws_iam_role_policy_attachment" {"pipe_policy_attachment" [{"policy_arn" "${aws_iam_policy.pipe_policy.arn}",
                                                                            "role" "${aws_iam_role.pipe_role.name}"}]},
               "aws_lambda_permission" {(str "events_" "invitecustomer") [{"action" "lambda:InvokeFunction",
                                                                           "function_name" "${module.lambda_function_invitecustomer.lambda_function_name}",
                                                                           "principal" "events.amazonaws.com",
                                                                           "source_arn" "${module.eventbridge.eventbridge_rule_arns[\"all_events\"]}"}]},
               "aws_pipes_pipe" {"events_pipe" [{"name" "events-pipe",
                                                 "role_arn" "${aws_iam_role.pipe_role.arn}",
                                                 "source" "${module.dynamodb_table_events.dynamodb_table_stream_arn}",
                                                 "source_parameters" [{"dynamodb_stream_parameters" [{"batch_size" 1,
                                                                                                      "starting_position" "LATEST"}]}],
                                                 "target" "${module.eventbridge.eventbridge_bus_arn}"}]}}
   "provider" {"aws" [{"default_tags" [{"tags" {"Component" "aws-serverless"}}]
                       "region" "eu-west-1"}]}

   ;"terraform" {"backend" {"s3" {}}, "required_providers" {"aws" {"source" "aws", "version" "5.44.0"}}}
   })

