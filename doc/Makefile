
THUMBNAILS=	command_page-thumb.png host_page-thumb.png \
		permission_page-thumb.png redirection_page-thumb.png
CONVERT=	convert -thumbnail 640x

all: index.html $(THUMBNAILS)

.SUFFIXES: .rst .html .svg .png

.rst.html:
	rst2html --stylesheet-path=default.css --link-stylesheet \
	--strip-comments < $< > $@

.svg.png:
	inkscape --export-png $@ $<

clean:
	rm -f *.html

command_page-thumb.png: command_page.png
	$(CONVERT) command_page.png command_page-thumb.png

host_page-thumb.png: host_page.png
	$(CONVERT) host_page.png host_page-thumb.png

permission_page-thumb.png: permission_page.png
	$(CONVERT) permission_page.png permission_page-thumb.png

redirection_page-thumb.png: redirection_page.png
	$(CONVERT) redirection_page.png redirection_page-thumb.png
