#include "offset_tables.hpp"
#include "signature_manager.hpp"
#include "slime_log.hpp"

namespace slime {
namespace {

void registerSignatures(SignatureManager& mgr) {
    // Function / code signatures to scan in libminecraftpe.so (or equivalent).
    // Pattern format: IDA-style "F4 0F 1E F8 ?? ?? 00 91" — empty = unresolved.
    const char* kSigs[] = {
        // Core loop / instance
        "ClientInstance::update",
        "ClientInstance::getLocalPlayer",
        "MinecraftGame::onDeviceLost",
        "MinecraftGame::update",
        // Actor / player
        "Actor::baseTick",
        "Actor::getNameTag",
        "Player::getName",
        "Player::normalTick",
        "Mob::getJumpPower",
        // Combat / interaction
        "GameMode::attack",
        "GameMode::buildBlock",
        "GameMode::useItem",
        // Render / camera / visual modules
        "Camera::getFov",
        "Options::getGamma",
        "FogManager::getFogDistance",
        "LevelRendererPlayer::render",
        "ItemInHandRenderer::render",
        "GuiData::displayClientMessage",
        "MinecraftUIRenderContext::getTexture",
        // World / network
        "Level::getRuntimeActorList",
        "Level::tick",
        "RakNetInstance::tick",
        "RaknetConnector::getPeer",
        // Input
        "ClientInputHandler::handle",
        "MoveInputHandler::tick",
        // Screen
        "HudScreenController::tick",
        "ChatScreenController::tick",
    };
    for (const char* n : kSigs) {
        mgr.addSignature(n, ""); // fill after RE for target arm64 build
    }
}

void registerOffsetSlots(SignatureManager& mgr) {
    // Member / vtable index offsets (Flarial-style names).
    // Value 0 = unset for this Android build until RE confirms.
    const char* kOffsets[] = {
        // ClientInstance
        "ClientInstance::guiData",
        "ClientInstance::minecraftGame",
        "ClientInstance::levelRenderer",
        "ClientInstance::camera",
        "ClientInstance::getPacketSender",
        "ClientInstance::getFovX",
        "ClientInstance::getFovY",
        "ClientInstance::viewMatrix",
        "ClientInstance::getLevelRenderer",
        // MinecraftGame
        "MinecraftGame::textureGroup",
        "MinecraftGame::gameRenderer",
        "MinecraftGame::gameRendererW2S",
        // Level / world
        "Level::getPlayerMap",
        "Level::getRuntimeActorList",
        "Level::hitResult",
        "Level::mItemRegistry",
        "LevelData::worldName",
        "LevelData::isMultiplayerGame",
        "Dimension::weather",
        // Actor / player
        "Actor::hurtTime",
        "Actor::mAlias",
        "Actor::baseTickVft",
        "Actor::getAnimationComponent",
        "Actor::mActorRendererIdHash",
        "Actor::mActorRendererIdStr",
        "Player::onGround",
        "Player::playerName",
        "Player::playerInventory",
        "Player::gamemode",
        "PlayerInventory::inventory",
        // Block / item
        "Block::blockLegacy",
        "BlockLegacy::name",
        "BlockLegacy::namespace",
        "BlockLegacy::mMapColor",
        "BlockLegacy::mDefaultState",
        "Item::mBlockType",
        "ItemActor::stack",
        "ItemRegistry::mNameToItemMap",
        "ItemRegistry::mTileItemNameToItemMap",
        // Render
        "LevelRender::getLevelRendererPlayer",
        "LevelRendererPlayer::cameraPos",
        "ScreenContext::tessellator",
        "MinecraftUIRenderContext::textures",
        "TextureGroup::loadedTextures",
        "ItemRenderer::armorRenderInfo",
        "ArmorRenderInfo::typeIndex",
        "SwingAngle",
        // Attributes
        "Attribute::Health",
        "Attribute::Hunger",
        "Attribute::Saturation",
        "Attribute::PlayerLevel",
        "Attribute::PlayerExperience",
        // Network
        "RaknetConnector::JoinedIp",
        "RaknetConnector::RawIp",
        "RaknetConnector::port",
        "NetworkSystem::remoteConnectorComposite",
        // UI controllers (vtable indices often)
        "ContainerScreenController::_handlePlaceAll",
        "ContainerScreenController::_handlePlaceOne",
        "ChatScreenController::refreshChatMessages",
        // Animation
        "AnimationComponent::mLastReloadInitTimeStampClient",
        // Custom render
        "CustomRenderComponent::renderer",
        "UIControl::children",
        "UIControl::components",
    };
    for (const char* n : kOffsets) {
        mgr.addOffset(n, 0);
    }
}

/** Optional: document Windows reference values from open Flarial 1.21.13x for *comparison only*. */
void annotateWindowsReferenceComments() {
    // Not applied to Android mgr — different binary.
    // ClientInstance::guiData Win 0x648, camera 0x358, levelRenderer 0x1B8 — x86_64 only.
    SLOG_I("Windows Flarial offsets are reference-only; Android table uses 0 until RE");
}

void loadTable(SignatureManager& mgr, const char* versionLabel) {
    mgr.clear();
    registerSignatures(mgr);
    registerOffsetSlots(mgr);
    annotateWindowsReferenceComments();
    SLOG_I("offset table loaded: %s (sigs=%d offsets registered, patterns empty)",
           versionLabel, mgr.totalCount());
}

void init_26_80(SignatureManager& mgr) {
    loadTable(mgr, "26.80.x Android arm64");
    // When RE is done, set concrete values, e.g.:
    // mgr.addOffset("ClientInstance::guiData", 0x...);
    // mgr.addSignature("Options::getGamma", "F4 0F 1E F8 ?? ?? ...");
}

void init_26_70(SignatureManager& mgr) {
    loadTable(mgr, "26.70.x Android arm64");
}

void init_26_60(SignatureManager& mgr) {
    loadTable(mgr, "26.60.x Android arm64");
}

void init_26_40(SignatureManager& mgr) {
    loadTable(mgr, "26.40.x Android arm64");
}

void init_generic(SignatureManager& mgr) {
    loadTable(mgr, "generic Android fallback");
}

} // namespace

void registerOffsetsForVersion(const std::string& versionId) {
    auto& mgr = SignatureManager::instance();
    if (versionId.rfind("26.80", 0) == 0 || versionId.rfind("26.81", 0) == 0) {
        init_26_80(mgr);
    } else if (versionId.rfind("26.70", 0) == 0 || versionId.rfind("26.72", 0) == 0) {
        init_26_70(mgr);
    } else if (versionId.rfind("26.60", 0) == 0) {
        init_26_60(mgr);
    } else if (versionId.rfind("26.40", 0) == 0) {
        init_26_40(mgr);
    } else {
        init_generic(mgr);
    }
}

const VersionInfo* supportedVersions(int* outCount) {
    static const VersionInfo kVersions[] = {
        {"26.80", "26.80.x Release", "arm64"},
        {"26.70", "26.70.x Release", "arm64"},
        {"26.60", "26.60.x Release", "arm64"},
        {"26.40", "26.40.x Release", "arm64"},
    };
    if (outCount) *outCount = 4;
    return kVersions;
}

std::vector<std::pair<std::string, int>> listOffsets() {
    return SignatureManager::instance().offsets();
}

std::vector<std::pair<std::string, bool>> listSignatureStatus() {
    std::vector<std::pair<std::string, bool>> out;
    for (const auto& e : SignatureManager::instance().signatures()) {
        out.emplace_back(e.name, e.resolved);
    }
    return out;
}

} // namespace slime
