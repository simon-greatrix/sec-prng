# sec-prng : Secure pseudo-random number generation

## TL;DR

JavaDoc for this project can be found [here](http://simon-greatrix.github.io/sec-prng/doc/).

## What is this?

Good cryptography needs securely generated random numbers. Java does not provide a random number generator that meets the needs of modern strong cryptography. This library fixes that.

## How do I use it?

Read the installation instructions. Use it.

## Installation Instructions

To use this library in an application, you should do one of the following:

### As a normal security Provider

```java
SecureRandom rand = new SecureRandom("Nist-SHA256",new prng.SecureRandomProvider());
```

### As a default security Provider

```java
prng.SecureRandomProvider.install(true);
```

Installs the secure random provider as the default secure random provider, so calls to new SecureRandom() will use on of its algorithms.

### As an application override

```
java -javaagent:prng-1.0.0.jar ...rest of command line
```

Install the secure random provider and the default secure random number generator implementation prior to invoking the application's main method.

### As a platform extension

The provider may be made a standard provider for all applications using a given Java Runtime Environment.

1. Copy `prng-1.0.0.jar` to `[java home]/jre/lib/ext`
2. Open the file `[java home]/jre/lib/security/java.security` in a text editor.
3. Add a line such as:
   `security.provider.1=prng.SecureRandomProvider`
4. Adjust all the other `security.provider.N` entries so each one has a unique name with the order you desire
5. Save the file
