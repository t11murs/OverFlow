description = "OrderFlow identity-service"

dependencies {
    add("implementation", "io.jsonwebtoken:jjwt-api:0.13.0")
    add("runtimeOnly", "io.jsonwebtoken:jjwt-impl:0.13.0")
    add("runtimeOnly", "io.jsonwebtoken:jjwt-jackson:0.13.0")
}
