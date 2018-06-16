AbstractMenuAction : QObject {
	var <>action;

	initConnections {
		this.connectMethod('changed()', 'onChanged');
		this.connectMethod('triggered(bool)', 'onTriggered');
		this.connectMethod('hovered()', 'onHovered');
		this.connectMethod('toggled(bool)', 'onToggled');

		this.menuRole = \noRole;
	}

	onChanged 	{ this.changed(\changed) }
	onTriggered	{ |b| 	this.changed(\triggered, b); action.value(this, b); }
	onHovered 	{ this.changed(\hovered) }
	onToggled 	{ |b|	this.changed(\toggled, b) }

	menuRole 	{ 		^this.getProperty(\menuRole) }
	menuRole_ 	{ |b| 	^this.setProperty(\menuRole, QMenuRole(b)) }

	shortcut 	{ 		^this.getProperty(\shortcut) }
	shortcut_ 	{ |str| ^this.setProperty(\shortcut, str) }

	string 		{ 		^this.getProperty(\text) }
	string_	 	{ |str| ^this.setProperty(\text, str) }

	menu 		{ 		^this.getProperty(\menu) }
	menu_ 		{ |b| 	^this.setProperty(\menu, b) }

	asAction { ^this }
}

MenuAction : AbstractMenuAction {
	*qtClass { ^'QcAction' }

	*new {
		|string, function|
		^super.new.init().string_(string).action_(function);
	}

	*separator {
		|string|
		^MenuAction().separator_(true).string_(string)
	}

	init {
		this.initConnections();
	}

	checkable 	{ 		^this.getProperty(\checkable) }
	checkable_ 	{ |b| 	^this.setProperty(\checkable, b) }

	checked 	{ 		^this.getProperty(\checked) }
	checked_ 	{ |b| 	this.checkable = true; ^this.setProperty(\checked, b) }

	toolTip 	{ 		^this.getProperty(\toolTip) }
	toolTip_ 	{ |str| ^this.setProperty(\toolTip, str) }

	statusTip 	{ 		^this.getProperty(\statusTip) }
	statusTip_ 	{ |str| ^this.setProperty(\statusTip, str) }

	font 		{ 		^this.getProperty(\font) }
	font_ 		{ |font| ^this.setProperty(\font, font) }

	separator 	{ 		^this.getProperty(\separator) }
	separator_	{ |b| 	^this.setProperty(\separator, b) }

	icon_ 	{
		|icon|
		this.iconVisible = icon.notNil;
		^this.setProperty(\icon, icon)
	}

	iconVisible	{ |b| 	^this.getProperty(\iconVisibleInMenu) }
	iconVisible_{ |b| 	^this.setProperty(\iconVisibleInMenu, b) }

	asMenu {
		this.menu = Menu().title_(this.string);
		^this.menu;
	}

	printOn { arg stream; stream << this.class.name << "(\"" << (this.string ? "-")  << "\")" }
}

CustomViewAction : AbstractMenuAction {
	*new {
		|view, function|
		^super.new.init().defaultView_(view).action_(function)
	}

	*qtClass { ^'QcWidgetAction' }

	init {
		this.initConnections
	}

	defaultView { 		^this.getProperty(\defaultWidget) }
	defaultView_{ |v| 	^this.setProperty(\defaultWidget, v) }
}

MainMenu {
	classvar <otherMenus;
	classvar <applicationMenu;
	classvar <serversMenu;
	classvar <registered;
	classvar systemMenus;
	classvar <>buildAppMenusAction;

	*initClass {
		serversMenu = Menu(MenuAction.separator).title_("Servers");

		applicationMenu = Menu(
			MenuAction("Stop", { CmdPeriod.run; }).shortcut_("Ctrl+."),
			serversMenu,
			MenuAction.separator,
			MenuAction("Quit", { 0.exit; }).shortcut_("Ctrl+Q")
		).title_("SuperCollider");

		applicationMenu.addDependant({
			|menu, what|
			if (what == \aboutToShow) {
				{ this.prUpdateServersMenu() }.defer;
			}
		});
		Server.all.do(_.addDependant({
			{ this.prUpdateServersMenu() }.defer
		}));

		registered = List();

		systemMenus = [\SuperCollider, \File, \Edit, \Server, \Quarks, \Help];
		systemMenus.do({ |systemMenu| this.prGetMenu(systemMenu) });

		this.prUpdateServersMenu();
		this.clear();
		this.prUpdate();
	}

	*register {
		|action, menu, group=\none|
		var menuList, existingIndex;

		menu = menu.asSymbol;
		group = group.asSymbol;

		menuList = this.prGetMenuGroup(menu, group);
		existingIndex = menuList.detectIndex({ |existing| existing.string == action.string });
		if (existingIndex.notNil) {
			"Menu item '%' replaced an existing menu".format(action.string).warn;
			menuList[existingIndex] = action;
		} {
			menuList.add(action);
		};

		this.prUpdate();
	}

	*unregister {
		|removeAction|
		registered.do {
			|menuAssoc|
			menuAssoc.value.do {
				|groupAssoc|
				groupAssoc.value.do {
					|action, i|
					if (removeAction == action) {
						groupAssoc.value.removeAt(i);
						^this.prUpdate();
					}
				}
			}
		}
	}

	*registerQuarkMenu {
		|quarkName, menu|
		menu.title = quarkName;
		this.register(menu, \Quarks);
	}

	*unregisterQuarkMenu {
		|quarkName|
		var menuList, existingIndex;
		menuList = this.prGetMenuGroup(\Quarks, \none);
		existingIndex = menuList.detectIndex({ |existing| existing.string.asSymbol == quarkName.asSymbol });
		if (existingIndex.notNil) {
			menuList.removeAt(existingIndex)
		};

		this.prUpdate();
	}

	*clear {
		otherMenus = [];
		this.prUpdate();
	}

	*add {
		|menu|
		otherMenus = otherMenus.add(menu);
		this.prUpdate();
	}

	*remove {
		|menu|
		otherMenus = otherMenus.remove(menu);
		this.prUpdate();
	}

	*insert {
		|index, menu|
		otherMenus = otherMenus.insert(index, menu);
		this.prUpdate();
	}

	*prGetMenu {
		|name|
		var menu, insertIndex;
		menu = registered.detect({ |m| m.isKindOf(Association) and: { m.key == name } });

		if (menu.notNil) {
			menu = menu.value;
		} {
			menu = List.newFrom([ \none -> List() ]);
			insertIndex = systemMenus.detectIndex(_ == name);
			if (insertIndex.isNil) {
				insertIndex = registered.size();
			};
			registered.insert(insertIndex, name -> menu);
		};

		^menu;
					}

	*prGetMenuGroup {
		|menuName, groupName|
		var menu, group;

		menu = this.prGetMenu(menuName);
		group = menu.detect({ |g| g.isKindOf(Association) and: { g.key == groupName } });

		if (group.notNil) {
			group = group.value;
		} {
			group = List();
			menu.add(groupName -> group);
				};

		^group;
	}

	*prUpdateServersMenu {
		serversMenu.clear();
		Server.all.do {
			|s|
			var running, options, kill, default;
			var startString, runningString, defaultString;

			startString = if (s.serverRunning, "Stop", "Boot");
			runningString = if (s.serverRunning, "(running)", "(stopped)");
			defaultString = if (s == Server.default, "◎", " ");

			running = MenuAction(startString);
			running.action = {
				if (s.serverRunning) {
					s.quit;
				} {
					s.boot;
				}
			};
			if ((s == Server.default) && s.serverRunning.not) {
				running.shortcut = "Ctrl+B";
			};

			kill = MenuAction("Kill");
			kill.action = { s.kill };

			options = MenuAction("Options...");
			options.action_({
				\ServerOptionsGui.asClass.new(s);
			});

			serversMenu.addAction(MenuAction(defaultString + s.name + runningString).font_(Font(italic:true)));
			serversMenu.addAction(running);

			if (s.serverRunning) {
				serversMenu.addAction(kill)
			};

			if (\ServerOptionsGui.asClass.notNil) {
				serversMenu.addAction(options);
			};

			serversMenu.addAction(MenuAction.separator);
		};

		serversMenu.addAction(MenuAction("Kill All", { Server.killAll; }));
	}


	*prBuildAppMenus {
		if (buildAppMenusAction.notNil) {
			^buildAppMenusAction.value(registered);
		} {
			^registered.collect {
				|entry|
				var menu, menuName, menuItems;

				menuName = entry.key;
				menuItems = entry.value;

				menu = Menu().title_(menuName.asString);

				menuItems.do {
					|item, i|
					var groupName, groupItems;
					groupName = item.key;
					groupItems = item.value;

					if (i != 0) {
						menu.addAction(MenuAction.separator.string_(groupName));
					};

					groupItems.do {
						|groupItem|
						menu.addAction(groupItem);
					}
				};

				menu
			};
		}
	}

	*prUpdate {
		var menus = this.prBuildAppMenus();
		var actualotherMenus = ([applicationMenu] ++ otherMenus ++ menus).asArray();
		this.prSetAppMenus(actualotherMenus);
	}

	*prSetAppMenus {
		|menus|
		_Qt_SetAppMenus
	}
}

AbstractActionView : View {
	actions {
		^this.getProperty(\actions);
	}

	clear {
		this.invokeMethod('clear');
	}

	addAction {
		|action|
		^this.invokeMethod('addAction', action.asAction);
	}

	removeAction {
		|action|
		^this.invokeMethod('removeAction', action.asAction);
	}

	insertAction {
		|before, action|
		if (before.isKindOf(Number)) {
			before = this.actions[before]
		};

		^this.invokeMethod('insertAction', before, action.asAction);
	}
}

Menu : AbstractActionView {
	*qtClass { ^'QcMenu' }

	*new {
		|...actions|
		var menu = super.new.init();
		actions.do {
			|a|
			menu.addAction(a);
		};
		^menu;
	}

	*newFrom {
		|otherMenu|
		^otherMenu.copy
	}

	init {
		this.connectMethod('triggered(QAction*)', 'onTriggered');
		this.connectMethod('aboutToShow()', 'onShow' /*, true*/);   // @TODO - Is this safe? Deadlock potential?
		this.connectMethod('aboutToHide()', 'onHide');
		this.connectMethod('hovered(QAction*)', 'onHovered');
	}

	copy {
		var properties = [\title, \tearOff];
		var newMenu = Menu();

		this.actions.do {
			|action|
			newMenu.addAction(action)
		};

		properties.do {
			|prop|
			newMenu.perform(prop.asSetter, this.perform(prop))
		};

		^newMenu
	}

	title 			{ 		^this.getProperty(\title) }
	title_ 			{ |title| 	^this.setProperty(\title, title) }

	string			{ 			^this.title }
	string_			{ |title| 	^this.title_(title) }

	tearOff 		{ 		^this.getProperty(\tearOffEnabled) }
	tearOff_ 		{ |b| 	^this.setProperty(\tearOffEnabled, b) }

	onShow 			{		this.changed(\aboutToShow) }
	onHide			{ 		this.changed(\aboutToHide) }
	onTriggered 	{ |action| 	this.changed(\triggered, action) }
	onHovered 		{ |action|	this.changed(\hovered, action) }

	front {
		|point, action|
		point = point ?? QtGUI.cursorPosition;
		action = action ?? MenuAction();
		this.invokeMethod(\popup, [point, action])
	}

	asAction {
		^MenuAction(this.title).menu_(this)
	}

	asMenu {
		^this
	}

	remove {
		// Don't destroy menus when they are closed - just hide them
	}

	printOn { arg stream; stream << this.class.name << "(\"" << (this.title ? "-")  << "\")" }
}

ToolBar : AbstractActionView {
	*qtClass { ^'QcToolBar' }

	*new {
		|...actions|
		var toolbar = super.new.init();
		actions.do {
			|a|
			toolbar.addAction(a);
		};
		^toolbar;
	}

	init {}

	orientation { 		^this.getProperty(\orientation) }
	orientation_{ |i| 	^this.setProperty(\orientation, QOrientation(i)) }

	toolButtonStyle { 		^this.getProperty(\toolButtonStyle) }
	toolButtonStyle_{ |i| 	^this.setProperty(\toolButtonStyle, QToolButtonStyle(i)) }

	// These are not currently functional in an SC context
	movable 	{ 		^this.getProperty(\movable) }
	movable_ 	{ |b| 	^this.setProperty(\movable, b) }

	floatable 	{ 		^this.getProperty(\floatable) }
	floatable_ 	{ |b| 	^this.setProperty(\floatable, b) }

	floating 	{ 		^this.getProperty(\floating) }
	floating_ 	{ |b| 	^this.setProperty(\floating, b) }
	}

+View {
	asAction {
		|func|
		^CustomViewAction(this, func)
	}
}

+Function {
	asAction {
		^MenuAction().action_(this)
	}
}

+String {
	asAction {
		^MenuAction().string_(this)
	}
}

