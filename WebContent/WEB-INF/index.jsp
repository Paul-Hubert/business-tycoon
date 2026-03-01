<!DOCTYPE html>
<html lang="en">
	<head>
		<title>Trade Empire</title>
		<meta charset="utf-8">
		<meta name="viewport" content="width=device-width, initial-scale=1">
		<link rel="preconnect" href="https://fonts.googleapis.com">
		<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
		<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">
		<link href="/css/variables.css" rel="stylesheet">
		<link href="/css/layout.css" rel="stylesheet">
		<link href="/css/components.css" rel="stylesheet">
		<link href="/css/animations.css" rel="stylesheet">
		<link href="/css/common.css" rel="stylesheet">
	</head>
	<body class="login-page fade-in">

		<div class="login-card">
			<div class="login-card__logo">
				<div class="login-card__title">Trade Empire</div>
				<div class="login-card__subtitle">Build. Produce. Dominate.</div>
			</div>

			<%
			if(request.getAttribute("error") != null) {
			%>
			<div class="alert alert--error" role="alert">
				${requestScope.error}
			</div>
			<%
			}
			%>

			<form method="post" action="/">
				<div class="form-group">
					<label class="form-label" for="login-user">Username</label>
					<input type="text" class="form-input" id="login-user" name="user" placeholder="Enter username" required>
				</div>
				<div class="form-group">
					<label class="form-label" for="login-pass">Password</label>
					<input type="password" class="form-input" id="login-pass" name="pass" placeholder="Enter password" required>
				</div>
				<div class="form-row gap-3 mt-4">
					<button type="submit" name="action" value="login" class="btn btn--primary btn--lg flex-1">Login</button>
					<button type="submit" name="action" value="signup" class="btn btn--secondary btn--lg flex-1">Sign Up</button>
				</div>
			</form>

			<p class="text-center text-muted mt-4" style="font-size: var(--text-sm);">
				Compete with players worldwide to build the ultimate trade empire.
			</p>
		</div>

	</body>
</html>
