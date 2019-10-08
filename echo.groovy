
from('knative:endpoint/echo')
  .setHeader('CamelTelegramChatId').header('ce-chat')
  .transform().simple('Echo ${body}')
  .to('telegram:bots?authorizationToken=<the-bot-token>')
