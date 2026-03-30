with open('e:/NagramX/TMessagesProj/src/main/java/org/telegram/ui/Components/SharedMediaLayout.java', 'r', encoding='utf-8') as f:
    lines = f.readlines()

replacement = '''            if (iBlur3FactoryLiquidGlass != null) {
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
            }
'''

new_lines = lines[:3569] + [replacement] + lines[3580:]

with open('e:/NagramX/TMessagesProj/src/main/java/org/telegram/ui/Components/SharedMediaLayout.java', 'w', encoding='utf-8', newline='
') as f:
    f.writelines(new_lines)
