inf226 Mandatory 3

Task 0, part A,B:

Created two new immutable classes Password and UserName. Using SCrypt, we now store passwords safely using salt.
When trying to register the program will only accept passwords that meet NIST criteria. When logging in the program
will check if the password is infact stored in our database. 

Part C:
Added: response.addHeader("Set-Cookie", "session=" + session.identity.toString() + "; HttpOnly; SameSite=strict");
in Handler.java so that httpOnly flag is set correctly. When logged in the browser will now tell us that flags are
sat correctly.

