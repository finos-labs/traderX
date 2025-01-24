# FINOS | TraderX Sample Trading App | People Service

![DEV Only Warning](http://badgen.net/badge/warning/not-for-production/red)
![Local Dev Machine Supported](http://badgen.net/badge/windows-dev/supported/green)

## Description 
The people service is used for managing users in the system, and associating them with accounts.

 * Returns information about a person by logonId or employeeId
 * Returns the list of persons whose logonId or fullName containes the search text
 * Returns if the logonId or employeeId can be associated to a valid person

Default Port is 18089.

TODO: Get this port configurable by env var PEOPLE_SERVICE_PORT

## Building and Running
```bash
$ cd PeopleService.WebApi
$ dotnet run
```

## Accessing the Swagger URL

Visit the forwarded port `/swagger` to open the SwaggerUI.

Example URL:

`/People/GetPerson?LogonId=user01`
