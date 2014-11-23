grails.project.work.dir = 'target'

grails.project.dependency.resolution = {

    inherits 'global'
    log 'warn'

    repositories {
        grailsCentral()
        mavenLocal()
        mavenCentral()
    }

    dependencies {
        compile 'com.bugsnag:bugsnag:1.2.5'
    }

    plugins {
        build ':release:2.2.1', ':rest-client-builder:2.0.1', {
            export = false
        }
    }
}
