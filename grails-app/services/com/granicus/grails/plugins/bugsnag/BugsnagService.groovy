package com.granicus.grails.plugins.bugsnag

import grails.util.Environment

import com.bugsnag.Client
import com.bugsnag.MetaData

import org.codehaus.groovy.grails.web.context.ServletContextHolder as SCH

class BugsnagService {

    private static List COOKIES_TO_OBFUSCATE = ["JSESSIONID"]
    private static List HEADERS_TO_OBFUSCATE = ["X-Authorization-token", "X-Authorization-key"]

    def grailsApplication
    def exceptionHandler
    def grailsResourceLocator

    static transactional = false

    def addMetadata = null

    def getConfiguredClient(String context){

        log.info "getConfiguredClient()"

        // configure the client

        def conf = grailsApplication.config.grails.plugin.bugsnag

        if( !conf.enabled ){
            log.info "bugsnag plugin is not enabled. returning null."
            return null
        }

        if( !conf.containsKey('apikey') ){
            log.error "grails.plugin.bugsnag.apikey not configured. assign your bugsnag api key with this configuration value."
            return null
        }

        // create the bugsnag client
        def client = new Client( conf.apikey )

        // configure the release stage or set it to the current environment name
        client.setReleaseStage( conf.releasestage ?: Environment.current.name)

        // configure the context of the client
        if( context ){
            client.setContext( context )
        }
        else if( conf.containsKey('context') ){
            client.setContext( conf.context )
        }

      // set the application version
      client.setAppVersion( grailsApplication.metadata.getApplicationVersion() )

        if (conf.containsKey('projectPackages')) {
            client.setProjectPackages(*conf.projectPackages)
        }

      return client
    }

    def notify(request, exception, extraMetaData = [:]) {

        def client = getConfiguredClient(request.requestURI)

        try{
            def user = request.getAttribute("currentUserX")
            if (user) {
                client.setUser(user.id?.toString(), user.email, null)
            } else {
                client.setUser(request.request?.getRemoteAddr(), null, null)
            }

            MetaData metaData = new MetaData()

            metaData.addToTab( "app", "application name", grailsApplication.metadata.getApplicationName() )

            metaData.addToTab( "environment", "grails version", grailsApplication.metadata.getGrailsVersion() )
            metaData.addToTab( "environment", "java version", System.getProperty("java.version") )
            metaData.addToTab( "environment", "java vendor", System.getProperty("java.vendor") )
            metaData.addToTab( "environment", "os name", System.getProperty("os.name") )
            metaData.addToTab( "environment", "os version", System.getProperty("os.version") )
            metaData.addToTab( "environment", "servlet", SCH.servletContext.serverInfo )

            metaData.addToTab( "user", "remoteUser", request.remoteUser?:"(none)" )
            metaData.addToTab( "user", "userPrincipal", request.userPrincipal?:"(none)" )

            metaData.addToTab( "request", "requestURI", request.requestURI )
            metaData.addToTab( "request", "forwardURI", request.forwardURI)

            metaData.addToTab( "request", "cookies", request.cookies.collect {
                String value = (COOKIES_TO_OBFUSCATE.contains(it.name)) ? it.value : obfuscate(it.value)

                return "\nName: ${it.name}\nMax Age: ${it.maxAge}\nPath: ${it.path}\nSecure: ${it.secure}\nDomain: ${it.domain}\nVersion: ${it.version}\nValue: ${value}"
            }.join("\n"))

            metaData.addToTab( "request", "headers", request.headerNames.findAll{ it != 'cookie' }.collect{ headerName ->
                String headers
                if (HEADERS_TO_OBFUSCATE.contains(headerName)) {
                    headers = request.getHeaders(headerName).toList().collect{obfuscate(it)}.toString()
                } else {
                    headers = request.getHeaders(headerName).toList().toString()
                }
                return "${headerName}: ${headers}"
            }.join('\n') )
            metaData.addToTab( "request", "authType", request.authType )
            metaData.addToTab( "request", "method", request.method )
            metaData.addToTab( "request", "server", request.serverName?:"(none)" )
            metaData.addToTab( "request", "port", request.serverPort?:"(none)" )
            metaData.addToTab( "request", "content type", request.contentType?:"(none)" )
            metaData.addToTab( "request", "character encoding", request.characterEncoding?:"(none)" )
            metaData.addToTab( "request", "scheme", request.scheme?:"(none)" )
            metaData.addToTab( "request", "queryString", request.queryString?:"(none)" )
            metaData.addToTab( "request", "session", request.getSession(false)?.toString() )

            metaData.addToTab( "request", "xml", request.xml?.text() )
            metaData.addToTab( "request", "json", request.json?.text() )

            extraMetaData.each{ k, v -> 
              metaData.addToTab( "extra", k, v )
            }

            //TODO: get handler for including user defined metadata
            if( addMetadata instanceof groovy.lang.Closure ){
              try{
                addMetadata(metaData)
              }
              catch( excp ){
                log.error "error calling 'addMetadata' closure.", excp
              }
            }

            client.notify(exception,metaData)

        }catch( excp ){
            log.error "error calling notify", excp
        }
    }

    private String obfuscate(String str) {
        if (!str) return ""
        return str.take(6)
    }
}

