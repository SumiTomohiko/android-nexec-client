
CMD = ant
SRC_DIR = src
PKG = jp.gr.java_conf.neko_daisuki.android.nexec.client
PKG_DIR = $(SRC_DIR)/jp/gr/java_conf/neko_daisuki/android/nexec/client
X_PKG = au.com.darkside.XServer
X_PKG_DIR = $(SRC_DIR)/au/com/darkside/XServer

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
	@rm -f $(PKG) $(X_PKG)
	@ln -s $(PKG_DIR) $(PKG)
	@ln -s $(X_PKG_DIR) $(X_PKG)

.PHONY: doc
