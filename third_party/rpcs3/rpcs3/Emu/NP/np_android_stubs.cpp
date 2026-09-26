// =============================================================================
// PocoS3 Android stub for the NP protobuf-dependent module.
//
// On Android, RPCN/PSN online play is non-functional (Sony shut down PS3 PSN
// in 2021; RPCN custom servers don't apply to mobile-only use cases). To
// eliminate the protobuf dependency entirely, the protobuf-using .cpp files
//   * pb_helpers.cpp
//   * rpcn_client.cpp
//   * np_requests.cpp
//   * np_requests_gui.cpp
//   * np_notifications.cpp
//   * generated/np2_structs.pb.cc
// are excluded from the rpcs3_emu build on Android via if(NOT ANDROID) in
// Emu/CMakeLists.txt. This file provides empty-stub definitions for every
// external symbol that was previously defined in those files, so that
// np_handler.cpp and the cell modules (sceNp*.cpp, sys_net*.cpp, overlays)
// still link cleanly.
//
// All stubs return defaults (false / 0 / CELL_OK / empty containers).
// Calls into RPCN functionality silently no-op. Trophy unlocks, score
// recording, matchmaking etc. will silently fail; PS3 HLE returns the
// equivalent of "RPCN not connected" which PS3 games handle gracefully.
//
// To regenerate this file when upstream rpcs3 adds new np_handler methods,
// see tools/generate_np_stubs.py (re-extracts method signatures from the
// original .cpp files).
// =============================================================================

#include "stdafx.h"

#include "Emu/NP/np_handler.h"
#include "Emu/NP/rpcn_client.h"

LOG_CHANNEL(np_stubs_log, "np_stubs");

// -----------------------------------------------------------------------------
// rpcn:: free functions (declared in rpcn_client.h)
// -----------------------------------------------------------------------------
namespace rpcn
{
localized_string_id rpcn_state_to_localized_string_id(rpcn_state /*state*/)
{
    return localized_string_id::INVALID;
}

std::string rpcn_state_to_string(rpcn_state /*state*/)
{
    return "Stubbed on Android (RPCN disabled)";
}

void print_error(CommandType /*command*/, ErrorType /*error*/) {}

// -----------------------------------------------------------------------------
// rpcn::rpcn_client class methods (declared in rpcn_client.h)
// All return defaults; get_instance() returns nullptr which np_handler.cpp
// handles via null-shared_ptr checks before calling.
// -----------------------------------------------------------------------------
rpcn_client::rpcn_client(u32 /*binding_address*/) {}
rpcn_client::~rpcn_client() {}
std::shared_ptr<rpcn_client> rpcn_client::get_instance(u32 /*binding_address*/, bool /*check_config*/) { return nullptr; }
rpcn_state rpcn_client::wait_for_connection() { return rpcn_state::failure_no_failure; }
rpcn_state rpcn_client::wait_for_authentified() { return rpcn_state::failure_no_failure; }
bool rpcn_client::terminate_connection() { return false; }
void rpcn_client::reset_state() {}
void rpcn_client::get_friends(friend_data& /*friend_infos*/) {}
void rpcn_client::get_friends_and_register_cb(friend_data& /*friend_infos*/, friend_cb_func /*cb_func*/, void* /*cb_param*/) {}
void rpcn_client::register_friend_cb(friend_cb_func /*cb_func*/, void* /*cb_param*/) {}
void rpcn_client::remove_friend_cb(friend_cb_func /*cb_func*/, void* /*cb_param*/) {}
ErrorType rpcn_client::create_user(std::string_view /*npid*/, std::string_view /*password*/, std::string_view /*online_name*/, std::string_view /*avatar_url*/, std::string_view /*email*/) { return ErrorType::NotFound; }
ErrorType rpcn_client::resend_token(std::string_view /*npid*/, std::string_view /*password*/) { return ErrorType::NotFound; }
ErrorType rpcn_client::send_reset_token(std::string_view /*npid*/, std::string_view /*email*/) { return ErrorType::NotFound; }
ErrorType rpcn_client::reset_password(std::string_view /*npid*/, std::string_view /*token*/, std::string_view /*password*/) { return ErrorType::NotFound; }
ErrorType rpcn_client::delete_account() { return ErrorType::NotFound; }
ErrorType rpcn_client::delete_trophies(std::string_view /*communication_id*/) { return ErrorType::NotFound; }
std::optional<ErrorType> rpcn_client::add_friend(std::string_view /*friend_username*/) { return std::nullopt; }
bool rpcn_client::remove_friend(std::string_view /*friend_username*/) { return false; }
u32 rpcn_client::get_num_friends() const { return 0; }
u32 rpcn_client::get_num_blocks() const { return 0; }
std::optional<std::string> rpcn_client::get_friend_by_index(u32 /*index*/) const { return std::nullopt; }
std::optional<std::pair<std::string, friend_online_data>> rpcn_client::get_friend_presence_by_index(u32 /*index*/) const { return std::nullopt; }
std::optional<std::pair<std::string, friend_online_data>> rpcn_client::get_friend_presence_by_npid(const std::string& /*npid*/) const { return std::nullopt; }
std::vector<std::pair<NotificationType, std::vector<u8>>> rpcn_client::get_notifications() { return {}; }
std::map<u32, std::pair<CommandType, std::vector<u8>>> rpcn_client::get_replies() { return {}; }
std::unordered_map<std::string, friend_online_data> rpcn_client::get_presence_updates() { return {}; }
std::map<std::string, friend_online_data> rpcn_client::get_presence_states() { return {}; }
std::vector<u64> rpcn_client::get_new_messages() { return {}; }
std::optional<shared_ptr<std::pair<std::string, message_data>>> rpcn_client::get_message(u64 /*id*/) const { return std::nullopt; }
std::vector<std::pair<u64, shared_ptr<std::pair<std::string, message_data>>>> rpcn_client::get_messages_and_register_cb(SceNpBasicMessageMainType /*type*/, bool /*include_bootable*/, message_cb_func /*cb_func*/, void* /*cb_param*/) { return {}; }
void rpcn_client::remove_message_cb(message_cb_func /*cb_func*/, void* /*cb_param*/) {}
void rpcn_client::mark_message_used(u64 /*id*/) {}
bool rpcn_client::is_connected() const { return false; }
bool rpcn_client::is_authentified() const { return false; }
rpcn_state rpcn_client::get_rpcn_state() const { return rpcn_state::failure_no_failure; }
void rpcn_client::server_infos_updated() {}
bool rpcn_client::get_server_list(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, std::vector<u16>& /*server_list*/) { return false; }
u64 rpcn_client::get_network_time(u32 /*req_id*/) { return 0; }
bool rpcn_client::get_world_list(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, u16 /*server_id*/) { return false; }
bool rpcn_client::createjoin_room(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, const SceNpMatching2CreateJoinRoomRequest* /*req*/) { return false; }
bool rpcn_client::join_room(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, const SceNpMatching2JoinRoomRequest* /*req*/) { return false; }
bool rpcn_client::leave_room(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, const SceNpMatching2LeaveRoomRequest* /*req*/) { return false; }
bool rpcn_client::search_room(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, const SceNpMatching2SearchRoomRequest* /*req*/) { return false; }
bool rpcn_client::get_roomdata_external_list(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, const SceNpMatching2GetRoomDataExternalListRequest* /*req*/) { return false; }
bool rpcn_client::get_room_member_data_external_list(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, u64 /*room_id*/) { return false; }
bool rpcn_client::set_roomdata_external(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, const SceNpMatching2SetRoomDataExternalRequest* /*req*/) { return false; }
bool rpcn_client::get_roomdata_internal(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, const SceNpMatching2GetRoomDataInternalRequest* /*req*/) { return false; }
bool rpcn_client::set_roomdata_internal(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, const SceNpMatching2SetRoomDataInternalRequest* /*req*/) { return false; }
bool rpcn_client::get_roommemberdata_internal(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, const SceNpMatching2GetRoomMemberDataInternalRequest* /*req*/) { return false; }
bool rpcn_client::set_roommemberdata_internal(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, const SceNpMatching2SetRoomMemberDataInternalRequest* /*req*/) { return false; }
bool rpcn_client::set_userinfo(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, const SceNpMatching2SetUserInfoRequest* /*req*/) { return false; }
bool rpcn_client::ping_room_owner(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, u64 /*room_id*/) { return false; }
bool rpcn_client::send_room_message(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, const SceNpMatching2SendRoomMessageRequest* /*req*/) { return false; }
bool rpcn_client::req_sign_infos(u32 /*req_id*/, std::string_view /*npid*/) { return false; }
bool rpcn_client::req_ticket(u32 /*req_id*/, std::string_view /*service_id*/, const std::vector<u8>& /*cookie*/) { return false; }
bool rpcn_client::send_message(const message_data& /*msg_data*/, const std::set<std::string>& /*npids*/) { return false; }
bool rpcn_client::get_board_infos(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, SceNpScoreBoardId /*board_id*/) { return false; }
bool rpcn_client::record_score(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, SceNpScoreBoardId /*board_id*/, SceNpScorePcId /*char_id*/, SceNpScoreValue /*score*/, const std::optional<std::string> /*comment*/, const std::optional<std::vector<u8>> /*score_data*/) { return false; }
bool rpcn_client::get_score_range(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, SceNpScoreBoardId /*board_id*/, u32 /*start_rank*/, u32 /*num_rank*/, bool /*with_comment*/, bool /*with_gameinfo*/) { return false; }
bool rpcn_client::get_score_npid(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, SceNpScoreBoardId /*board_id*/, const std::vector<std::pair<SceNpId, s32>>& /*npids*/, bool /*with_comment*/, bool /*with_gameinfo*/) { return false; }
bool rpcn_client::get_score_friend(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, SceNpScoreBoardId /*board_id*/, bool /*include_self*/, bool /*with_comment*/, bool /*with_gameinfo*/, u32 /*max_entries*/) { return false; }
bool rpcn_client::record_score_data(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, SceNpScorePcId /*pc_id*/, SceNpScoreBoardId /*board_id*/, s64 /*score*/, const std::vector<u8>& /*score_data*/) { return false; }
bool rpcn_client::get_score_data(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, SceNpScorePcId /*pc_id*/, SceNpScoreBoardId /*board_id*/, const SceNpId& /*npid*/) { return false; }
bool rpcn_client::tus_set_multislot_variable(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, const SceNpOnlineId& /*targetNpId*/, vm::cptr<SceNpTusSlotId> /*slotIdArray*/, vm::cptr<s64> /*variableArray*/, s32 /*arrayNum*/, bool /*vuser*/) { return false; }
bool rpcn_client::tus_get_multislot_variable(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, const SceNpOnlineId& /*targetNpId*/, vm::cptr<SceNpTusSlotId> /*slotIdArray*/, s32 /*arrayNum*/, bool /*vuser*/) { return false; }
bool rpcn_client::tus_get_multiuser_variable(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, const std::vector<SceNpOnlineId>& /*targetNpIdArray*/, SceNpTusSlotId /*slotId*/, s32 /*arrayNum*/, bool /*vuser*/) { return false; }
bool rpcn_client::tus_get_friends_variable(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, SceNpTusSlotId /*slotId*/, bool /*includeSelf*/, s32 /*sortType*/, u32 /*arrayNum*/) { return false; }
bool rpcn_client::tus_add_and_get_variable(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, const SceNpOnlineId& /*targetNpId*/, SceNpTusSlotId /*slotId*/, s64 /*inVariable*/, vm::ptr<SceNpTusAddAndGetVariableOptParam> /*option*/, bool /*vuser*/) { return false; }
bool rpcn_client::tus_try_and_set_variable(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, const SceNpOnlineId& /*targetNpId*/, SceNpTusSlotId /*slotId*/, s32 /*opeType*/, s64 /*variable*/, vm::ptr<SceNpTusTryAndSetVariableOptParam> /*option*/, bool /*vuser*/) { return false; }
bool rpcn_client::tus_delete_multislot_variable(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, const SceNpOnlineId& /*targetNpId*/, vm::cptr<SceNpTusSlotId> /*slotIdArray*/, s32 /*arrayNum*/, bool /*vuser*/) { return false; }
bool rpcn_client::tus_set_data(u32 /*req_id*/, SceNpCommunicationId& /*communication_id*/, const SceNpOnlineId& /*targetNpId*/, SceNpTusSlotId /*slotId*/, const std::vector<u8>& /*tus_data*/, vm::cptr<SceNpTusDataInfo> /*info*/, vm::ptr<SceNpTusSetDataOptParam> /*option*/, bool /*vuser*/) { return false; }
bool rpcn_client::tus_get_data(u32 /*req_id*/, SceNpCommunicationId& /*communication_id*/, const SceNpOnlineId& /*targetNpId*/, SceNpTusSlotId /*slotId*/, bool /*vuser*/) { return false; }
bool rpcn_client::tus_get_multislot_data_status(u32 /*req_id*/, SceNpCommunicationId& /*communication_id*/, const SceNpOnlineId& /*targetNpId*/, vm::cptr<SceNpTusSlotId> /*slotIdArray*/, s32 /*arrayNum*/, bool /*vuser*/) { return false; }
bool rpcn_client::tus_get_multiuser_data_status(u32 /*req_id*/, SceNpCommunicationId& /*communication_id*/, const std::vector<SceNpOnlineId>& /*targetNpIdArray*/, SceNpTusSlotId /*slotId*/, s32 /*arrayNum*/, bool /*vuser*/) { return false; }
bool rpcn_client::tus_get_friends_data_status(u32 /*req_id*/, SceNpCommunicationId& /*communication_id*/, SceNpTusSlotId /*slotId*/, bool /*includeSelf*/, s32 /*sortType*/, u32 /*arrayNum*/) { return false; }
bool rpcn_client::tus_delete_multislot_data(u32 /*req_id*/, SceNpCommunicationId& /*communication_id*/, const SceNpOnlineId& /*targetNpId*/, vm::cptr<SceNpTusSlotId> /*slotIdArray*/, s32 /*arrayNum*/, bool /*vuser*/) { return false; }
bool rpcn_client::send_presence(const SceNpCommunicationId& /*pr_com_id*/, const std::string& /*pr_title*/, const std::string& /*pr_status*/, const std::string& /*pr_comment*/, const std::vector<u8>& /*pr_data*/) { return false; }
bool rpcn_client::unlock_trophy(const SceNpCommunicationId& /*communication_id*/, s32 /*trophy_id*/, s64 /*timestamp*/) { return false; }
std::vector<std::pair<s32, s64>> rpcn_client::sync_trophies(const SceNpCommunicationId& /*communication_id*/, const std::vector<std::pair<s32, s64>>& /*local_unlocked*/) { return {}; }
bool rpcn_client::createjoin_room_gui(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, const SceNpMatchingAttr* /*attr_list*/) { return false; }
bool rpcn_client::join_room_gui(u32 /*req_id*/, const SceNpRoomId& /*room_id*/) { return false; }
bool rpcn_client::leave_room_gui(u32 /*req_id*/, const SceNpRoomId& /*room_id*/) { return false; }
bool rpcn_client::get_room_list_gui(u32 /*req_id*/, const SceNpCommunicationId& /*communication_id*/, const SceNpMatchingReqRange* /*range*/, vm::ptr<SceNpMatchingSearchCondition> /*cond*/, vm::ptr<SceNpMatchingAttr> /*attr*/) { return false; }
bool rpcn_client::set_room_search_flag_gui(u32 /*req_id*/, const SceNpRoomId& /*room_id*/, bool /*stealth*/) { return false; }
bool rpcn_client::get_room_search_flag_gui(u32 /*req_id*/, const SceNpRoomId& /*room_id*/) { return false; }
bool rpcn_client::set_room_info_gui(u32 /*req_id*/, const SceNpRoomId& /*room_id*/, vm::ptr<SceNpMatchingAttr> /*attrs*/) { return false; }
bool rpcn_client::get_room_info_gui(u32 /*req_id*/, const SceNpRoomId& /*room_id*/, vm::ptr<SceNpMatchingAttr> /*attrs*/) { return false; }
bool rpcn_client::quickmatch_gui(u32 /*req_id*/, const SceNpCommunicationId& /*com_id*/, vm::cptr<SceNpMatchingSearchCondition> /*cond*/, s32 /*available_num*/) { return false; }
bool rpcn_client::searchjoin_gui(u32 /*req_id*/, const SceNpCommunicationId& /*com_id*/, vm::cptr<SceNpMatchingSearchCondition> /*cond*/, vm::cptr<SceNpMatchingAttr> /*attr*/) { return false; }
const std::string& rpcn_client::get_online_name() const { static const std::string e; return e; }
const std::string& rpcn_client::get_avatar_url() const { static const std::string e; return e; }
u32 rpcn_client::get_addr_sig() const { return 0; }
u16 rpcn_client::get_port_sig() const { return 0; }
u32 rpcn_client::get_addr_local() const { return 0; }
void rpcn_client::update_local_addr(u32 /*addr*/) {}
} // namespace rpcn

// -----------------------------------------------------------------------------
// np_handler methods (originally defined in np_requests.cpp)
// -----------------------------------------------------------------------------
namespace np {
std::vector<SceNpMatching2ServerId> np_handler::get_match2_server_list(SceNpMatching2ContextId ctx_id) { return {}; }
u64 np_handler::get_network_time() { return 0; }
u32 np_handler::get_server_status(SceNpMatching2ContextId ctx_id, vm::cptr<SceNpMatching2RequestOptParam> optParam, u16 server_id) { return 0; }
u32 np_handler::create_server_context(SceNpMatching2ContextId ctx_id, vm::cptr<SceNpMatching2RequestOptParam> optParam, u16) { return 0; }
u32 np_handler::delete_server_context(SceNpMatching2ContextId ctx_id, vm::cptr<SceNpMatching2RequestOptParam> optParam, u16) { return 0; }
u32 np_handler::get_world_list(SceNpMatching2ContextId ctx_id, vm::cptr<SceNpMatching2RequestOptParam> optParam, u16 server_id) { return 0; }
void np_handler::reply_get_world_list(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
u32 np_handler::create_join_room(SceNpMatching2ContextId ctx_id, vm::cptr<SceNpMatching2RequestOptParam> optParam, const SceNpMatching2CreateJoinRoomRequest* req) { return 0; }
void np_handler::reply_create_join_room(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
u32 np_handler::join_room(SceNpMatching2ContextId ctx_id, vm::cptr<SceNpMatching2RequestOptParam> optParam, const SceNpMatching2JoinRoomRequest* req) { return 0; }
void np_handler::reply_join_room(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
u32 np_handler::leave_room(SceNpMatching2ContextId ctx_id, vm::cptr<SceNpMatching2RequestOptParam> optParam, const SceNpMatching2LeaveRoomRequest* req) { return 0; }
void np_handler::reply_leave_room(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
u32 np_handler::search_room(SceNpMatching2ContextId ctx_id, vm::cptr<SceNpMatching2RequestOptParam> optParam, const SceNpMatching2SearchRoomRequest* req) { return 0; }
void np_handler::reply_search_room(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
u32 np_handler::get_roomdata_external_list(SceNpMatching2ContextId ctx_id, vm::cptr<SceNpMatching2RequestOptParam> optParam, const SceNpMatching2GetRoomDataExternalListRequest* req) { return 0; }
void np_handler::reply_get_roomdata_external_list(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
u32 np_handler::get_room_member_data_external_list(SceNpMatching2ContextId ctx_id, vm::cptr<SceNpMatching2RequestOptParam> optParam, const SceNpMatching2GetRoomMemberDataExternalListRequest* req) { return 0; }
void np_handler::reply_get_room_member_data_external_list(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
u32 np_handler::set_roomdata_external(SceNpMatching2ContextId ctx_id, vm::cptr<SceNpMatching2RequestOptParam> optParam, const SceNpMatching2SetRoomDataExternalRequest* req) { return 0; }
void np_handler::reply_set_roomdata_external(u32 req_id, rpcn::ErrorType error) {}
u32 np_handler::get_roomdata_internal(SceNpMatching2ContextId ctx_id, vm::cptr<SceNpMatching2RequestOptParam> optParam, const SceNpMatching2GetRoomDataInternalRequest* req) { return 0; }
void np_handler::reply_get_roomdata_internal(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
u32 np_handler::set_roomdata_internal(SceNpMatching2ContextId ctx_id, vm::cptr<SceNpMatching2RequestOptParam> optParam, const SceNpMatching2SetRoomDataInternalRequest* req) { return 0; }
void np_handler::reply_set_roomdata_internal(u32 req_id, rpcn::ErrorType error) {}
u32 np_handler::get_roommemberdata_internal(SceNpMatching2ContextId ctx_id, vm::cptr<SceNpMatching2RequestOptParam> optParam, const SceNpMatching2GetRoomMemberDataInternalRequest* req) { return 0; }
void np_handler::reply_get_roommemberdata_internal(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
u32 np_handler::set_roommemberdata_internal(SceNpMatching2ContextId ctx_id, vm::cptr<SceNpMatching2RequestOptParam> optParam, const SceNpMatching2SetRoomMemberDataInternalRequest* req) { return 0; }
void np_handler::reply_set_roommemberdata_internal(u32 req_id, rpcn::ErrorType error) {}
u32 np_handler::set_userinfo(SceNpMatching2ContextId ctx_id, vm::cptr<SceNpMatching2RequestOptParam> optParam, const SceNpMatching2SetUserInfoRequest* req) { return 0; }
void np_handler::reply_set_userinfo(u32 req_id, rpcn::ErrorType error) {}
u32 np_handler::get_ping_info(SceNpMatching2ContextId ctx_id, vm::cptr<SceNpMatching2RequestOptParam> optParam, const SceNpMatching2SignalingGetPingInfoRequest* req) { return 0; }
void np_handler::reply_get_ping_info(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
u32 np_handler::send_room_message(SceNpMatching2ContextId ctx_id, vm::cptr<SceNpMatching2RequestOptParam> optParam, const SceNpMatching2SendRoomMessageRequest* req) { return 0; }
void np_handler::reply_send_room_message(u32 req_id, rpcn::ErrorType error) {}
void np_handler::req_sign_infos(std::string_view npid, u32 conn_id) {}
void np_handler::reply_req_sign_infos(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
u32 np_handler::get_lobby_info_list(SceNpMatching2ContextId ctx_id, vm::cptr<SceNpMatching2RequestOptParam> optParam, const SceNpMatching2GetLobbyInfoListRequest* req) { return 0; }
void np_handler::req_ticket(u32 version, const SceNpId* npid, const char* service_id, const u8* cookie, u32 cookie_size, const char* entitlement_id, u32 consumed_count) {}
void np_handler::reply_req_ticket(u32, rpcn::ErrorType error, vec_stream& reply) {}
void np_handler::transaction_async_handler(std::unique_lock<shared_mutex> lock, const shared_ptr<generic_async_transaction_context>& trans_ctx, u32 req_id, bool async) {}
void np_handler::get_board_infos(shared_ptr<score_transaction_ctx>& trans_ctx, SceNpScoreBoardId boardId, vm::ptr<SceNpScoreBoardInfo> boardInfo, bool async) {}
void np_handler::reply_get_board_infos(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
void np_handler::record_score(shared_ptr<score_transaction_ctx>& trans_ctx, SceNpScoreBoardId boardId, SceNpScoreValue score, vm::cptr<SceNpScoreComment> scoreComment, const u8* data, u32 data_size, vm::ptr<SceNpScoreRankNumber> tmpRank, bool async) {}
void np_handler::reply_record_score(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
void np_handler::record_score_data(shared_ptr<score_transaction_ctx>& trans_ctx, SceNpScoreBoardId boardId, SceNpScoreValue score, u32 totalSize, u32 sendSize, const u8* score_data, bool async) {}
void np_handler::reply_record_score_data(u32 req_id, rpcn::ErrorType error) {}
void np_handler::get_score_data(shared_ptr<score_transaction_ctx>& trans_ctx, SceNpScoreBoardId boardId, const SceNpId& npId, vm::ptr<u32> totalSize, u32 recvSize, vm::ptr<void> score_data, bool async) {}
void np_handler::reply_get_score_data(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
void np_handler::get_score_range(shared_ptr<score_transaction_ctx>& trans_ctx, SceNpScoreBoardId boardId, SceNpScoreRankNumber startSerialRank, vm::ptr<SceNpScoreRankData> rankArray, u32 rankArraySize, vm::ptr<SceNpScoreComment> commentArray, u32 commentArraySize, vm::ptr<void> infoArray, u32 infoArraySize, u32 arrayNum, vm::ptr<CellRtcTick> lastSortDate, vm::ptr<SceNpScoreRankNumber> totalRecord, bool async, bool deprecated) {}
void np_handler::handle_GetScoreResponse(u32 req_id, rpcn::ErrorType error, vec_stream& reply, bool simple_result) {}
void np_handler::reply_get_score_range(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
void np_handler::get_score_friend(shared_ptr<score_transaction_ctx>& trans_ctx, SceNpScoreBoardId boardId, bool include_self, vm::ptr<SceNpScoreRankData> rankArray, u32 rankArraySize, vm::ptr<SceNpScoreComment> commentArray, u32 commentArraySize, vm::ptr<void> infoArray, u32 infoArraySize, u32 arrayNum, vm::ptr<CellRtcTick> lastSortDate, vm::ptr<SceNpScoreRankNumber> totalRecord, bool async, bool deprecated) {}
void np_handler::reply_get_score_friends(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
void np_handler::get_score_npid(shared_ptr<score_transaction_ctx>& trans_ctx, SceNpScoreBoardId boardId, const std::vector<std::pair<SceNpId, s32>>& npid_vec, vm::ptr<SceNpScorePlayerRankData> rankArray, u32 rankArraySize, vm::ptr<SceNpScoreComment> commentArray, u32 commentArraySize, vm::ptr<void> infoArray, u32 infoArraySize, u32 arrayNum, vm::ptr<CellRtcTick> lastSortDate, vm::ptr<SceNpScoreRankNumber> totalRecord, bool async, bool deprecated) {}
void np_handler::reply_get_score_npid(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
void np_handler::handle_tus_no_data(u32 req_id, rpcn::ErrorType error) {}
void np_handler::handle_TusVarResponse(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
void np_handler::handle_TusVariable(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
void np_handler::handle_TusDataStatusResponse(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
void np_handler::tus_set_multislot_variable(shared_ptr<tus_transaction_ctx>& trans_ctx, const SceNpOnlineId& targetNpId, vm::cptr<SceNpTusSlotId> slotIdArray, vm::cptr<s64> variableArray, s32 arrayNum, bool vuser, bool async) {}
void np_handler::reply_tus_set_multislot_variable(u32 req_id, rpcn::ErrorType error) {}
void np_handler::tus_get_multislot_variable(shared_ptr<tus_transaction_ctx>& trans_ctx, const SceNpOnlineId& targetNpId, vm::cptr<SceNpTusSlotId> slotIdArray, vm::ptr<SceNpTusVariable> variableArray, s32 arrayNum, bool vuser, bool async) {}
void np_handler::reply_tus_get_multislot_variable(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
void np_handler::tus_get_multiuser_variable(shared_ptr<tus_transaction_ctx>& trans_ctx, std::vector<SceNpOnlineId> targetNpIdArray, SceNpTusSlotId slotId, vm::ptr<SceNpTusVariable> variableArray, s32 arrayNum, bool vuser, bool async) {}
void np_handler::reply_tus_get_multiuser_variable(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
void np_handler::tus_get_friends_variable(shared_ptr<tus_transaction_ctx>& trans_ctx, SceNpTusSlotId slotId, s32 includeSelf, s32 sortType, vm::ptr<SceNpTusVariable> variableArray, s32 arrayNum, bool async) {}
void np_handler::reply_tus_get_friends_variable(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
void np_handler::tus_add_and_get_variable(shared_ptr<tus_transaction_ctx>& trans_ctx, const SceNpOnlineId& targetNpId, SceNpTusSlotId slotId, s64 inVariable, vm::ptr<SceNpTusVariable> outVariable, vm::ptr<SceNpTusAddAndGetVariableOptParam> option, bool vuser, bool async) {}
void np_handler::reply_tus_add_and_get_variable(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
void np_handler::tus_try_and_set_variable(shared_ptr<tus_transaction_ctx>& trans_ctx, const SceNpOnlineId& targetNpId, SceNpTusSlotId slotId, s32 opeType, s64 variable, vm::ptr<SceNpTusVariable> resultVariable, vm::ptr<SceNpTusTryAndSetVariableOptParam> option, bool vuser, bool async) {}
void np_handler::reply_tus_try_and_set_variable(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
void np_handler::tus_delete_multislot_variable(shared_ptr<tus_transaction_ctx>& trans_ctx, const SceNpOnlineId& targetNpId, vm::cptr<SceNpTusSlotId> slotIdArray, s32 arrayNum, bool vuser, bool async) {}
void np_handler::reply_tus_delete_multislot_variable(u32 req_id, rpcn::ErrorType error) {}
void np_handler::tus_set_data(shared_ptr<tus_transaction_ctx>& trans_ctx, const SceNpOnlineId& targetNpId, SceNpTusSlotId slotId, u32 totalSize, u32 sendSize, vm::cptr<void> data, vm::cptr<SceNpTusDataInfo> info, vm::ptr<SceNpTusSetDataOptParam> option, bool vuser, bool async) {}
void np_handler::reply_tus_set_data(u32 req_id, rpcn::ErrorType error) {}
void np_handler::tus_get_data(shared_ptr<tus_transaction_ctx>& trans_ctx, const SceNpOnlineId& targetNpId, SceNpTusSlotId slotId, vm::ptr<SceNpTusDataStatus> dataStatus, vm::ptr<void> data, u32 recvSize, bool vuser, bool async) {}
void np_handler::reply_tus_get_data(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
void np_handler::tus_get_multislot_data_status(shared_ptr<tus_transaction_ctx>& trans_ctx, const SceNpOnlineId& targetNpId, vm::cptr<SceNpTusSlotId> slotIdArray, vm::ptr<SceNpTusDataStatus> statusArray, s32 arrayNum, bool vuser, bool async) {}
void np_handler::reply_tus_get_multislot_data_status(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
void np_handler::tus_get_multiuser_data_status(shared_ptr<tus_transaction_ctx>& trans_ctx, std::vector<SceNpOnlineId> targetNpIdArray, SceNpTusSlotId slotId, vm::ptr<SceNpTusDataStatus> statusArray, s32 arrayNum, bool vuser, bool async) {}
void np_handler::reply_tus_get_multiuser_data_status(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
void np_handler::tus_get_friends_data_status(shared_ptr<tus_transaction_ctx>& trans_ctx, SceNpTusSlotId slotId, s32 includeSelf, s32 sortType, vm::ptr<SceNpTusDataStatus> statusArray, s32 arrayNum, bool async) {}
void np_handler::reply_tus_get_friends_data_status(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
void np_handler::tus_delete_multislot_data(shared_ptr<tus_transaction_ctx>& trans_ctx, const SceNpOnlineId& targetNpId, vm::cptr<SceNpTusSlotId> slotIdArray, s32 arrayNum, bool vuser, bool async) {}
void np_handler::reply_tus_delete_multislot_data(u32 req_id, rpcn::ErrorType error) {}
} // namespace np
// -----------------------------------------------------------------------------
// np_handler methods (originally defined in np_requests_gui.cpp)
// -----------------------------------------------------------------------------
namespace np {
void np_handler::set_current_gui_ctx_id(u32 id) {}
void np_handler::set_gui_result(s32 event, np::event_data data) {}
error_code np_handler::get_matching_result(u32 ctx_id, u32 req_id, vm::ptr<void> buf, vm::ptr<u32> size, vm::ptr<s32> event) { return CELL_OK; }
error_code np_handler::get_result_gui(vm::ptr<void> buf, vm::ptr<u32> size, vm::ptr<s32> event) { return CELL_OK; }
error_code np_handler::create_room_gui(u32 ctx_id, vm::cptr<SceNpCommunicationId> communicationId, vm::cptr<SceNpMatchingAttr> attr, vm::ptr<SceNpMatchingGUIHandler> handler, vm::ptr<void> arg) { return CELL_OK; }
void np_handler::reply_create_room_gui(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
error_code np_handler::join_room_gui(u32 ctx_id, vm::ptr<SceNpRoomId> roomid, vm::ptr<SceNpMatchingGUIHandler> handler, vm::ptr<void> arg) { return CELL_OK; }
void np_handler::reply_join_room_gui(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
error_code np_handler::leave_room_gui(u32 ctx_id, vm::cptr<SceNpRoomId> roomid) { return CELL_OK; }
void np_handler::reply_leave_room_gui(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
error_code np_handler::get_room_list_gui(u32 ctx_id, vm::cptr<SceNpCommunicationId> communicationId, vm::ptr<SceNpMatchingReqRange> range, vm::ptr<SceNpMatchingSearchCondition> cond, vm::ptr<SceNpMatchingAttr> attr, vm::ptr<SceNpMatchingGUIHandler> handler, vm::ptr<void> arg, bool limit) { return CELL_OK; }
void np_handler::reply_get_room_list_gui(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
error_code np_handler::set_room_search_flag_gui(u32 ctx_id, vm::ptr<SceNpLobbyId>, vm::ptr<SceNpRoomId> room_id, s32 flag) { return CELL_OK; }
void np_handler::reply_set_room_search_flag_gui(u32 req_id, rpcn::ErrorType error) {}
error_code np_handler::get_room_search_flag_gui(u32 ctx_id, vm::ptr<SceNpLobbyId>, vm::ptr<SceNpRoomId> room_id) { return CELL_OK; }
void np_handler::reply_get_room_search_flag_gui(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
error_code np_handler::set_room_info_gui(u32 ctx_id, vm::ptr<SceNpLobbyId>, vm::ptr<SceNpRoomId> room_id, vm::ptr<SceNpMatchingAttr> attr) { return CELL_OK; }
void np_handler::reply_set_room_info_gui(u32 req_id, rpcn::ErrorType error) {}
error_code np_handler::get_room_info_gui(u32 ctx_id, vm::ptr<SceNpLobbyId>, vm::ptr<SceNpRoomId> room_id, vm::ptr<SceNpMatchingAttr> attr) { return CELL_OK; }
void np_handler::reply_get_room_info_gui(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
error_code np_handler::quickmatch_gui(u32 ctx_id, vm::cptr<SceNpCommunicationId> communicationId, vm::cptr<SceNpMatchingSearchCondition> cond, s32 available_num, s32 timeout, vm::ptr<SceNpMatchingGUIHandler> handler, vm::ptr<void> arg) { return CELL_OK; }
void np_handler::reply_quickmatch_gui(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
error_code np_handler::searchjoin_gui(u32 ctx_id, vm::cptr<SceNpCommunicationId> communicationId, vm::cptr<SceNpMatchingSearchCondition> cond, vm::cptr<SceNpMatchingAttr> attr, vm::ptr<SceNpMatchingGUIHandler> handler, vm::ptr<void> arg) { return CELL_OK; }
void np_handler::reply_searchjoin_gui(u32 req_id, rpcn::ErrorType error, vec_stream& reply) {}
error_code np_handler::get_room_member_list_local_gui(u32 ctx_id, vm::ptr<SceNpRoomId> room_id, vm::ptr<u32> buflen, vm::ptr<void> buf) { return CELL_OK; }
} // namespace np
// -----------------------------------------------------------------------------
// np_handler methods (originally defined in np_notifications.cpp)
// -----------------------------------------------------------------------------
namespace np {
void np_handler::notif_user_joined_room(vec_stream& noti) {}
void np_handler::notif_user_left_room(vec_stream& noti) {}
void np_handler::notif_room_destroyed(vec_stream& noti) {}
void np_handler::notif_updated_room_data_internal(vec_stream& noti) {}
void np_handler::notif_updated_room_member_data_internal(vec_stream& noti) {}
void np_handler::notif_room_message_received(vec_stream& noti) {}
void np_handler::notif_signaling_helper(vec_stream& noti) {}
void np_handler::generic_gui_notification_handler(vec_stream& noti, std::string_view name, s32 notification_type) {}
void np_handler::notif_member_joined_room_gui(vec_stream& noti) {}
void np_handler::notif_member_left_room_gui(vec_stream& noti) {}
void np_handler::notif_room_disappeared_gui(vec_stream& noti) {}
void np_handler::notif_room_owner_changed_gui(vec_stream& noti) {}
void np_handler::notif_user_kicked_gui(vec_stream& noti) {}
void np_handler::notif_quickmatch_complete_gui(vec_stream& noti) {}
} // namespace np
