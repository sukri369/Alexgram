import sys

tgt = '''            // NagramX: use solid pill bg - blur factory needs setSourceRootView() (missing in NagramX)
            {
                scrollSlidingTextTabStrip.setPadding(0, dp(7), 0, dp(7));
                scrollSlidingTextTabStrip.setClipToPadding(false);
                GradientDrawable pillBg = new GradientDrawable();
                pillBg.setCornerRadius(dp(18));
                pillBg.setColor(Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundGray), 0.9f));
                scrollSlidingTextTabStrip.setBackground(pillBg);
                scrollSlidingTextTabStrip.setOpen(false);
                addView(scrollSlidingTextTabStrip, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 50, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 0, 0, 0));
            }'''

rep = '''            if (iBlur3FactoryLiquidGlass != null) {
                BlurredBackgroundDrawable filterTabsViewBackground = iBlur3FactoryLiquidGlass.create(scrollSlidingTextTabStrip, BlurredBackgroundProviderImpl.topPanel(resourcesProvider));
                filterTabsViewBackground.setRadius(dp(18));
                filterTabsViewBackground.setPadding(dp(6.666f));
                scrollSlidingTextTabStrip.setPadding(0, dp(7), 0, dp(7));
                scrollSlidingTextTabStrip.setClipToPadding(false);
                scrollSlidingTextTabStrip.setBackground(null);
                scrollSlidingTextTabStrip.setBlurredBackground(filterTabsViewBackground);
                scrollSlidingTextTabStrip.setOpen(false);
                addView(scrollSlidingTextTabStrip, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 50, Gravity.CENTER_HORIZONTAL | Gravity.TOP, -2, 0, -2, 0));
            } else {
                addView(scrollSlidingTextTabStrip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.TOP));
            }'''

with open('e:/NagramX/TMessagesProj/src/main/java/org/telegram/ui/Components/SharedMediaLayout.java', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace(tgt, rep)

with open('e:/NagramX/TMessagesProj/src/main/java/org/telegram/ui/Components/SharedMediaLayout.java', 'w', encoding='utf-8', newline='\n') as f:
    f.write(text)

