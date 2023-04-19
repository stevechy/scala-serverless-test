
# Introduction

This is an example project that deploys an AWS Lambda web app with a Scala.js frontend using the Serverless Framework.

I've added a [github pages](https://stevechy.github.io/scala-serverless-test/) site about this repo.  

The rough steps to build the package are:
- Install nvm and sbt
- Run `npm install` in the root project directory and the frontend project directory
- Run `sbt lambdaPackage`

To deploy:
- Copy `template.deploy-config.json` to `deploy-config.dev.json` and fill in your AWS S3 bucket details
- Setup an AWS profile for the AWS account that you want to deploy to
- Run `sbt lambdaPackage`
- Run `npx serverless deploy --aws-profile MYAWSPROFILENAME` to deploy the development version
