<html>
<head>
</head>
<body onload="document.getElementById('username').focus()">
<form method="POST" action="/loginpw">
<div>
<span style="display: inline-block; width: 7em">Username:</span><input type="text" name="username" id="username" size="15"/>
</div>
<div>
<span style="display: inline-block; width: 7em">Password:</span><input type="password" name="password" size="15"/>
</div>
<input type="submit"/>
</form>
</body>
</html>