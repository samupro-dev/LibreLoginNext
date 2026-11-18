/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.dialog;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.dialog.CommonDialogData;
import com.github.retrooper.packetevents.protocol.dialog.DialogAction;
import com.github.retrooper.packetevents.protocol.dialog.MultiActionDialog;
import com.github.retrooper.packetevents.protocol.dialog.action.DynamicCustomAction;
import com.github.retrooper.packetevents.protocol.dialog.body.DialogBody;
import com.github.retrooper.packetevents.protocol.dialog.body.PlainMessage;
import com.github.retrooper.packetevents.protocol.dialog.body.PlainMessageDialogBody;
import com.github.retrooper.packetevents.protocol.dialog.button.ActionButton;
import com.github.retrooper.packetevents.protocol.dialog.button.CommonButtonData;
import com.github.retrooper.packetevents.protocol.dialog.input.Input;
import com.github.retrooper.packetevents.protocol.dialog.input.TextInputControl;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.util.adventure.AdventureSerializer;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCustomClickAction;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerClearDialog;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerShowDialog;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.command.InvalidCommandArgument;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;
import xyz.kyngs.librelogin.common.util.ComponentBridge;
import xyz.kyngs.librelogin.common.util.GeneralUtil;
import xyz.kyngs.librelogin.lib.adventure.text.TextComponent;

/**
 * Login, registration, password reset, change-password and unregister dialogs (Minecraft 1.21.6+)
 * sent and answered through PacketEvents.
 *
 * <p>The dialog buttons use the {@code dynamic/custom} action: the client sends the typed values
 * back as a custom click packet, which is handled here and never reaches the backend server. Geyser
 * translates these dialogs into Bedrock forms and sends the same packet back. The values go through
 * the regular {@code /login} and {@code /register} command logic, so every check those commands
 * make still applies.
 *
 * @param <P> the platform player type
 * @param <S> the platform server type
 */
public class PacketEventsAuthDialogs<P, S> implements AuthDialogs<P>, PacketListener {

    private static final String NAMESPACE = "librelogin";
    private static final int WIDTH = 200;
    private static final int BUTTON_WIDTH = 150;

    /**
     * The dialog a player has open. Its submit action id is librelogin:{@code key}; the steps
     * before login cannot be closed (the player would be stuck without commands), the others can.
     */
    private enum Step {
        REGISTER("register", "dialog.register.", "librelogin.command.register", false),
        LOGIN("login", "dialog.login.", "librelogin.command.login", false),
        CODE("code", "dialog.code.", "librelogin.command.login", false),
        RESET("reset", "dialog.reset.", "librelogin.command.email", false),
        CHANGE_PASSWORD(
                "change-password",
                "dialog.change-password.",
                "librelogin.command.changepassword",
                true),
        UNREGISTER("unregister", "dialog.unregister.", "librelogin.command.unregister", true);

        private final String key;
        private final String messages;
        private final String permission;
        private final boolean closable;
        private final ResourceLocation action;

        Step(String key, String messages, String permission, boolean closable) {
            this.key = key;
            this.messages = messages;
            this.permission = permission;
            this.closable = closable;
            this.action = new ResourceLocation(NAMESPACE, key);
        }
    }

    private static final ResourceLocation EXIT = new ResourceLocation(NAMESPACE, "exit");
    private static final ResourceLocation CANCEL = new ResourceLocation(NAMESPACE, "cancel");
    private static final ResourceLocation FORGOT = new ResourceLocation(NAMESPACE, "forgot");
    private static final ResourceLocation BACK = new ResourceLocation(NAMESPACE, "back");

    private final AuthenticLibreLogin<P, S> plugin;
    private final boolean serverEncodesPackets;
    private final BiPredicate<P, String> permissionCheck;
    // Players currently shown a dialog; only their answers are handled and only they get a clear.
    private final Map<UUID, Step> open = new ConcurrentHashMap<>();
    // Password already verified for an account with 2FA, kept until the code step ends or the
    // player leaves: /login needs both at once.
    private final Map<UUID, String> verifiedPasswords = new ConcurrentHashMap<>();
    private volatile @Nullable PacketListenerCommon registered;

    /**
     * Creates the dialog handler.
     *
     * @param plugin the plugin instance
     * @param serverEncodesPackets true on a game server, where PacketEvents encodes packets with
     *     the server version (which must then support dialogs too); false on a proxy
     * @param permissionCheck tests whether a player has a permission
     */
    public PacketEventsAuthDialogs(
            AuthenticLibreLogin<P, S> plugin,
            boolean serverEncodesPackets,
            BiPredicate<P, String> permissionCheck) {
        this.plugin = plugin;
        this.serverEncodesPackets = serverEncodesPackets;
        this.permissionCheck = permissionCheck;
    }

    /** Starts listening for dialog answers. */
    public void register() {
        registered =
                PacketEvents.getAPI()
                        .getEventManager()
                        .registerListener(this, PacketListenerPriority.NORMAL);
    }

    /**
     * Stops listening for dialog answers. PacketEvents outlives this plugin, so a listener left
     * behind would keep the disabled plugin in memory and keep answering dialog clicks.
     */
    public void unregister() {
        var listener = registered;
        registered = null;
        if (listener != null) PacketEvents.getAPI().getEventManager().unregisterListener(listener);
        open.clear();
        verifiedPasswords.clear();
    }

    /**
     * Whether a client and server version can show dialogs.
     *
     * @param client the client version
     * @param server the server version encoding the packets, or {@code null} on a proxy
     * @return true if dialogs are supported
     */
    static boolean supports(@Nullable ClientVersion client, @Nullable ServerVersion server) {
        return client != null
                && client.isRelease()
                && client.isNewerThanOrEquals(ClientVersion.V_1_21_6)
                && (server == null || server.isNewerThanOrEquals(ServerVersion.V_1_21_6));
    }

    @Override
    public boolean show(P player, boolean login, @Nullable TextComponent error) {
        return show(player, login ? Step.LOGIN : Step.REGISTER, error);
    }

    @Override
    public boolean showChangePassword(P player) {
        return show(player, Step.CHANGE_PASSWORD, null);
    }

    @Override
    public boolean showUnregister(P player) {
        return show(player, Step.UNREGISTER, null);
    }

    private boolean mailEnabled() {
        return plugin.getEmailHandler() != null
                && plugin.getCommandProvider().getEmailCommand() != null;
    }

    private boolean show(P player, Step step, @Nullable TextComponent notice) {
        var user = playingUser(player);
        if (user == null) return false;

        var version = user.getClientVersion();

        var body = new ArrayList<DialogBody>();
        if (notice != null) body.add(text(version, notice));
        body.add(text(version, message(step.messages + "text")));

        // The 2FA code has its own step, asked only after the password was accepted: the login
        // dialog then does not reveal which accounts use 2FA and has no field to leave empty.
        var inputs = new ArrayList<Input>();
        switch (step) {
            case REGISTER -> {
                inputs.add(
                        passwordInput(version, DialogPayload.PASSWORD, step.messages + "password"));
                if (plugin.getConfiguration()
                        .get(ConfigurationKeys.REQUIRE_PASSWORD_CONFIRMATION)) {
                    inputs.add(
                            passwordInput(
                                    version,
                                    DialogPayload.PASSWORD_REPEAT,
                                    step.messages + "password-repeat"));
                }
            }
            case LOGIN, UNREGISTER ->
                    inputs.add(
                            passwordInput(
                                    version, DialogPayload.PASSWORD, step.messages + "password"));
            case CODE ->
                    inputs.add(
                            input(
                                    version,
                                    DialogPayload.CODE,
                                    "dialog.code.code",
                                    DialogPayload.MAX_CODE_LENGTH));
            case RESET -> {
                inputs.add(
                        input(
                                version,
                                DialogPayload.TOKEN,
                                "dialog.reset.token",
                                DialogPayload.MAX_TOKEN_LENGTH));
                inputs.add(passwordInput(version, DialogPayload.PASSWORD, "dialog.reset.password"));
                inputs.add(
                        passwordInput(
                                version,
                                DialogPayload.PASSWORD_REPEAT,
                                "dialog.reset.password-repeat"));
            }
            case CHANGE_PASSWORD -> {
                inputs.add(
                        passwordInput(
                                version,
                                DialogPayload.OLD_PASSWORD,
                                "dialog.change-password.old-password"));
                inputs.add(
                        passwordInput(
                                version,
                                DialogPayload.PASSWORD,
                                "dialog.change-password.password"));
                inputs.add(
                        passwordInput(
                                version,
                                DialogPayload.PASSWORD_REPEAT,
                                "dialog.change-password.password-repeat"));
            }
        }

        var buttons = new ArrayList<ActionButton>();
        buttons.add(button(version, step.messages + "button", step.action));
        if (step == Step.LOGIN && mailEnabled())
            buttons.add(button(version, "dialog.login.forgot-button", FORGOT));
        if (step == Step.RESET) buttons.add(button(version, "dialog.reset.back-button", BACK));
        // Before login the only way out is leaving the server; afterwards the dialog is optional.
        var exit =
                step.closable
                        ? button(version, "dialog.cancel-button", CANCEL)
                        : button(version, "dialog.exit-button", EXIT);

        // wait_for_response keeps the dialog on screen until the answer is handled.
        var common =
                new CommonDialogData(
                        convert(version, message(step.messages + "title")),
                        null,
                        step.closable,
                        false,
                        DialogAction.WAIT_FOR_RESPONSE,
                        body,
                        inputs);

        open.put(plugin.getPlatformHandle().getUUIDForPlayer(player), step);
        user.sendPacket(
                new WrapperPlayServerShowDialog(new MultiActionDialog(common, buttons, exit, 1)));
        return true;
    }

    @Override
    public boolean isOpen(UUID uuid) {
        return open.containsKey(uuid);
    }

    @Override
    public void clear(P player) {
        var uuid = plugin.getPlatformHandle().getUUIDForPlayer(player);
        verifiedPasswords.remove(uuid);
        if (open.remove(uuid) == null) return;
        var user = playingUser(player);
        if (user != null) user.sendPacket(new WrapperPlayServerClearDialog());
    }

    @Override
    public void forget(UUID uuid) {
        open.remove(uuid);
        verifiedPasswords.remove(uuid);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.CUSTOM_CLICK_ACTION) return;
        var packet = new WrapperPlayClientCustomClickAction(event);
        var id = packet.getId();
        if (!NAMESPACE.equals(id.getNamespace())) return;
        event.setCancelled(true);

        Object target = event.getPlayer();
        var playerClass = plugin.getPlatformHandle().getPlayerClass();
        if (!playerClass.isInstance(target)) return;
        var player = playerClass.cast(target);
        var uuid = plugin.getPlatformHandle().getUUIDForPlayer(player);
        var step = uuid == null ? null : open.get(uuid);
        if (step == null) return;

        // Everything below is decided here, before any work is queued, so a flood of forged
        // packets cannot fill the shared executor that every login and command runs on.
        var key = id.getKey();
        if (key.equals("exit") && !step.closable) {
            if (open.remove(uuid) != null) {
                verifiedPasswords.remove(uuid);
                GeneralUtil.runAsync(
                        () -> plugin.getPlatformHandle().kick(player, message("dialog.exit-kick")));
            }
            return;
        }
        if (key.equals("cancel") && step.closable) {
            clear(player);
            return;
        }
        // Only the open dialog's own actions; answers beyond the rate limit get no reply.
        boolean valid =
                key.equals(step.key)
                        || (key.equals("forgot") && step == Step.LOGIN && mailEnabled())
                        || (key.equals("back") && step == Step.RESET);
        if (!valid) return;
        if (plugin.getCommandProvider().getLimiter().tryAndLimit(uuid)) return;
        var payload = DialogPayload.read(packet.getPayload());
        GeneralUtil.runAsync(() -> handle(player, uuid, step, key, payload));
    }

    private void handle(P player, UUID uuid, Step step, String key, DialogPayload payload) {
        if (key.equals("back")) {
            show(player, Step.LOGIN, null);
            return;
        }
        boolean forgot = key.equals("forgot");
        var permission = forgot ? "librelogin.command.email" : step.permission;
        var provider = plugin.getCommandProvider();
        var audience = plugin.getPlatformHandle().getAudienceForPlayer(player);
        var password =
                step == Step.CODE
                        ? verifiedPasswords.get(uuid)
                        : payload.password() == null ? "" : payload.password();
        if (password == null) {
            // The code step outlived its password (e.g. a reload): start again from the password.
            show(player, Step.LOGIN, null);
            return;
        }
        var repeat = payload.passwordRepeat();

        CompletionStage<Void> result;
        try {
            // The permission check ACF runs before the command (the rate limit was applied when
            // the packet arrived).
            if (!permissionCheck.test(player, permission)) {
                throw new InvalidCommandArgument(message("error.no-permission"));
            }
            // A new password must be a single word: /login could never accept one with spaces.
            boolean newPassword = !forgot && step != Step.CODE && step != Step.UNREGISTER;
            if (newPassword
                    && (hasWhitespace(password) || (repeat != null && hasWhitespace(repeat)))) {
                throw new InvalidCommandArgument(message("error.password-contains-spaces"));
            }
            if (forgot) {
                result = provider.getEmailCommand().onResetPassword(audience);
            } else {
                result =
                        switch (step) {
                            case REGISTER ->
                                    provider.getRegisterCommand()
                                            .onRegister(audience, password, repeat);
                            case LOGIN ->
                                    provider.getLoginCommand().onLogin(audience, password, null);
                            case CODE ->
                                    provider.getLoginCommand()
                                            .onLogin(audience, password, payload.code());
                            case RESET ->
                                    provider.getEmailCommand()
                                            .onConfirmPassReset(
                                                    audience,
                                                    nonNull(payload.token()),
                                                    password,
                                                    nonNull(repeat));
                            case CHANGE_PASSWORD -> {
                                if (!password.equals(repeat)) {
                                    throw new InvalidCommandArgument(
                                            message("error.password-not-match"));
                                }
                                yield provider.getChangePasswordCommand()
                                        .onPasswordChange(
                                                audience, nonNull(payload.oldPassword()), password);
                            }
                            case UNREGISTER ->
                                    provider.getUnregisterCommand()
                                            .onUnregister(audience, password);
                        };
            }
        } catch (InvalidCommandArgument e) {
            result = CompletableFuture.failedFuture(e);
        }

        result.whenComplete((ignored, error) -> {
            if (!open.containsKey(uuid)) return;
            if (error == null) {
                onSuccess(player, step, forgot);
                return;
            }
            var cause =
                    error instanceof CompletionException && error.getCause() != null
                            ? error.getCause()
                            : error;
            TextComponent shown = null;
            if (cause instanceof InvalidCommandArgument invalid) {
                shown = invalid.getErrorMessage();
            } else {
                plugin.getLogger()
                        .error("An unexpected exception occurred while handling a dialog", cause);
            }
            onFailure(player, uuid, step, forgot, password, shown);
        });
    }

    // Login and registration are closed by authorize(); the other steps move on here.
    private void onSuccess(P player, Step step, boolean forgot) {
        if (forgot) {
            show(player, Step.RESET, message("info.reset-password-mail-sent"));
        } else if (step == Step.RESET) {
            show(player, Step.LOGIN, message("info.password-reset"));
        } else if (step == Step.CHANGE_PASSWORD) {
            clear(player);
        }
    }

    private void onFailure(
            P player,
            UUID uuid,
            Step step,
            boolean forgot,
            String password,
            @Nullable TextComponent shown) {
        if (forgot) {
            show(player, Step.LOGIN, shown);
        } else if (step == Step.LOGIN && isMessage(shown, "totp.not-provided")) {
            // Right password, the account uses 2FA: ask for the code.
            verifiedPasswords.put(uuid, password);
            show(player, Step.CODE, null);
        } else if (step == Step.CODE
                && !(isMessage(shown, "totp.wrong") || isMessage(shown, "totp.not-provided"))) {
            // Any other failure (password changed meanwhile, lockout, ...) restarts the login.
            verifiedPasswords.remove(uuid);
            show(player, Step.LOGIN, shown);
        } else {
            show(player, step, shown);
        }
    }

    private static String nonNull(@Nullable String value) {
        return value == null ? "" : value;
    }

    private boolean isMessage(@Nullable TextComponent shown, String key) {
        return shown != null && shown.equals(message(key));
    }

    private static boolean hasWhitespace(String value) {
        return value.chars().anyMatch(Character::isWhitespace);
    }

    private com.github.retrooper.packetevents.protocol.player.@Nullable User playingUser(P player) {
        var user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null || user.getEncoderState() != ConnectionState.PLAY) return null;
        var server =
                serverEncodesPackets ? PacketEvents.getAPI().getServerManager().getVersion() : null;
        return supports(user.getClientVersion(), server) ? user : null;
    }

    private TextComponent message(String key) {
        return plugin.getMessages().getMessage(key);
    }

    private static net.kyori.adventure.text.Component convert(
            ClientVersion version, TextComponent component) {
        // Our Adventure is relocated, PacketEvents uses the platform's: convert through JSON.
        return AdventureSerializer.serializer(version).fromJson(ComponentBridge.toJson(component));
    }

    private static DialogBody text(ClientVersion version, TextComponent component) {
        return new PlainMessageDialogBody(new PlainMessage(convert(version, component), WIDTH));
    }

    private Input passwordInput(ClientVersion version, String key, String labelKey) {
        return input(version, key, labelKey, DialogPayload.MAX_PASSWORD_LENGTH);
    }

    private Input input(ClientVersion version, String key, String labelKey, int maxLength) {
        return new Input(
                key,
                new TextInputControl(
                        WIDTH, convert(version, message(labelKey)), true, "", maxLength, null));
    }

    private ActionButton button(ClientVersion version, String labelKey, ResourceLocation id) {
        return new ActionButton(
                new CommonButtonData(convert(version, message(labelKey)), null, BUTTON_WIDTH),
                new DynamicCustomAction(id, null));
    }
}
