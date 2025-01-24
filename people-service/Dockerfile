FROM --platform=$BUILDPLATFORM mcr.microsoft.com/dotnet/sdk:8.0-noble AS build
ARG TARGETARCH
WORKDIR /source
COPY PeopleService.WebApi/PeopleService.WebApi.csproj PeopleService.WebApi/
COPY PeopleService.Core/PeopleService.Core.csproj PeopleService.Core/
RUN dotnet restore PeopleService.WebApi/PeopleService.WebApi.csproj -a $TARGETARCH
COPY PeopleService.WebApi/ PeopleService.WebApi/
COPY PeopleService.Core/ PeopleService.Core/

FROM build AS publish
WORKDIR /source/PeopleService.WebApi
RUN dotnet publish PeopleService.WebApi.csproj -p:PublishSingleFile=true -a $TARGETARCH --self-contained true -c release -o /app

FROM mcr.microsoft.com/dotnet/runtime-deps:8.0-noble-chiseled
WORKDIR /app
COPY --from=publish /app .
EXPOSE 18089
ENV ASPNETCORE_HTTP_PORTS=18089
ENV ASPNETCORE_ENVIRONMENT=Development
ENTRYPOINT ["./PeopleService.WebApi"]
