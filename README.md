inf226 Mandatory 3

Task 0, part A,B:

Created two new immutable classes Password and UserName.

Part C:
Added: response.addHeader("Set-Cookie", "session=" + session.identity.toString() + "; HttpOnly; SameSite=strict");
in Handler.java so that httpOnly flag is set correctly. When logged in the browser will now tell us that flags are
sat correctly.

