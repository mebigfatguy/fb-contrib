<xsl:transform version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:template match="/MessageCollection">
		<html>
			<head>
				<title>fb-contrib: Bug Descriptions</title>
				<style type="text/css">
					h1 { font-size: 30px; }
					ul { display: block; list-style-type: none; width: 60%; }
					.bugcode { font-weight: bold; font-size: 18px; padding-left: 10px; border-left: 0.5em solid #6666FF; }
					.bugdetail { font-size: 16px; margin: 4px; padding: 5px 20px 15px 40px; border-left: 1px solid #6666FF; }
				</style>
			</head>
			<body background="true">
				<div style="position:absolute;top:0;left:0;width:256;height:65535;z-index:1;background-image:url(blend.jpg);">
				</div>

				<div style="position:absolute;top:20;left:20;z-index:2;">
					<h1>fb-contrib: Bug Descriptions</h1>
					
					<ul>
						<xsl:for-each select="BugCode">
							<xsl:sort select="@abbrev"/>
							<xsl:call-template name="BugCode"/>
						</xsl:for-each>
					</ul>
				</div>
			</body>
		</html>
	</xsl:template>
	
	<xsl:template match="BugCode" name="BugCode">
		<xsl:call-template name="Pattern">
			<xsl:with-param name="abbrev"><xsl:value-of select="@abbrev"/></xsl:with-param>
		</xsl:call-template>
	</xsl:template>
	
	<xsl:template name="Pattern">
		<xsl:param name="abbrev"/>
			<xsl:for-each select="//BugPattern[starts-with(@type,$abbrev)]">
				<xsl:sort select="."/>
				<li>
					<div class="bugcode">
						<xsl:value-of select="@type"/>
					</div>
					<div class="bugdetail">
						<xsl:value-of select="normalize-space(Details/text())" disable-output-escaping="yes"/>
					</div>
				</li>
			</xsl:for-each>
	</xsl:template>

</xsl:transform>
