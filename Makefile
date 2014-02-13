
CMD = ant
PKG = jp.gr.java_conf.neko_daisuki.android.nexec.client
PKG_DIR = src/jp/gr/java_conf/neko_daisuki/android/nexec/client

all: apk

apk:
	@$(CMD)

release:
	@$(CMD) release

icon:
	@$(CMD) icon

clean:
	@$(CMD) clean

doc:
	@cd doc && $(MAKE)

prepare:
	@rm -f $(PKG)
	@ln -s $(PKG_DIR) $(PKG)

.PHONY: doc
